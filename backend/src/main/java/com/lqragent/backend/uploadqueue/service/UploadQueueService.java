package com.lqragent.backend.uploadqueue.service;

import com.lqragent.backend.agents.content.summaryanalyzer.service.ContentAnalyzerService;
import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.storage.QiniuStorageService;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.KbScope;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.TaskStatus;
import com.lqragent.backend.uploadqueue.entity.UploadAnalysisHistory;
import com.lqragent.backend.uploadqueue.repository.KbUploadTaskRepository;
import com.lqragent.backend.uploadqueue.repository.UploadAnalysisHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadQueueService {

    private static final int BATCH_SIZE = 5;

    private final KbUploadTaskRepository taskRepository;
    private final UploadAnalysisHistoryRepository uploadAnalysisHistoryRepository;
    private final AiServerClient aiServerClient;
    private final ContentAnalyzerService contentAnalyzerService;
    private final AppRuntimeConfig runtimeConfig;
    private final QiniuStorageService qiniuStorageService;

    @Transactional
    public KbUploadTask enqueue(Long userId, String fileName, String filePath, KbScope scope) {
        KbUploadTask task = KbUploadTask.builder()
                .userId(userId)
                .fileName(fileName)
                .filePath(filePath)
                .kbScope(scope)
                .status(TaskStatus.PENDING)
                .build();
        return taskRepository.save(task);
    }

    public List<KbUploadTask> listByUser(Long userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<KbUploadTask> listRecent(int limit) {
        return taskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public KbUploadTask getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("上传任务不存在: " + id));
    }

    public KbUploadTask getTaskByIdForUser(Long id, Long userId) {
        return taskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Upload task not found or access denied: " + id));
    }

    @Transactional
    public void deleteTask(Long id) {
        KbUploadTask task = taskRepository.findById(id).orElse(null);
        uploadAnalysisHistoryRepository.deleteByUploadTaskId(id);
        log.info("[UploadQueue] Deleted analysis history for task: {}", id);
        if (task != null && task.getFilePath() != null) {
            try {
                qiniuStorageService.delete(task.getFilePath());
                log.info("[UploadQueue] Deleted from Qiniu: {}", task.getFilePath());
            } catch (Exception e) {
                log.warn("[UploadQueue] Failed to delete from Qiniu: {}", e.getMessage());
            }
        }
        taskRepository.deleteById(id);
        log.info("[UploadQueue] Deleted task: {}", id);
    }

    @Transactional
    public void deleteTaskForUser(Long id, Long userId) {
        KbUploadTask task = getTaskByIdForUser(id, userId);
        deleteTask(task.getId());
    }

    public long totalCount() {
        return taskRepository.count();
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            map.put(status.name(), taskRepository.countByStatus(status));
        }
        return map;
    }

    public boolean processOnePending() {
        return taskRepository.findFirstByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus.PENDING)
                .map(task -> {
                    processTask(task);
                    return true;
                })
                .orElse(false);
    }

    @Scheduled(fixedDelayString = "${upload.queue.worker-interval-ms:30000}")
    @Transactional
    public void processNext() {
        var batch = taskRepository.findTopNByStatusOrderByPriorityDescCreatedAtAsc(
                TaskStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        for (KbUploadTask task : batch) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("[UploadQueue] Batch item failed: task={}, error={}", task.getId(), e.getMessage());
            }
        }
    }

    private void processTask(KbUploadTask task) {
        log.info("[UploadQueue] Processing task id={}, file={}, scope={}",
                task.getId(), task.getFileName(), task.getKbScope());

        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        updateTaskProgress(task, 0, "正在下载文件");

        try {
            String kbName = resolveKbName(task);

            byte[] content = qiniuStorageService.download(task.getFilePath());
            String mimeType = detectMimeType(task.getFileName());

            boolean kbExists = knowledgeBaseExists(kbName);
            updateTaskProgress(task, 10, kbExists ? "正在上传到已有知识库" : "正在创建知识库并上传");

            Map<String, Object> submitResponse = kbExists
                    ? aiServerClient.uploadDocument(kbName, task.getFileName(), content, mimeType)
                    : aiServerClient.createKnowledgeBase(kbName, task.getFileName(), content, mimeType);

            String aiTaskId = asText(submitResponse.get("task_id"));
            waitForKnowledgeProcessing(task, kbName, aiTaskId);

            updateTaskProgress(task, 95, "正在生成摘要与知识点映射");
            var analysis = contentAnalyzerService.analyze(kbName, task.getFileName());
            task.setAnalysisResult(analysis.toJson());
            task.setMappedKpIds(String.join(",", analysis.mappedKpIds()));
            task.setStatus(TaskStatus.COMPLETED);
            task.setStatusMessage("已完成");
            task.setProgressPercent(100);

            log.info("[UploadQueue] Task {} completed, KB={}, mapped KPs: {}",
                    task.getId(), kbName, analysis.mappedKpIds());
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setStatusMessage("处理失败");
            task.setErrorMessage(e.getMessage());
            log.error("[UploadQueue] Task {} failed: {}", task.getId(), e.getMessage(), e);
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            KbUploadTask savedTask = taskRepository.save(task);
            saveAnalysisHistory(savedTask);
        }
    }

    private void waitForKnowledgeProcessing(KbUploadTask task, String kbName, String expectedTaskId) throws InterruptedException {
        while (true) {
            Map<String, Object> progress = aiServerClient.getProgress(kbName);
            String progressTaskId = asText(progress.get("task_id"));
            if (expectedTaskId != null && progressTaskId != null && !expectedTaskId.equals(progressTaskId)) {
                Thread.sleep(2000);
                continue;
            }

            String stage = asText(progress.get("stage"));
            Integer percent = asInteger(progress.get("progress_percent"));
            String message = firstNonBlank(
                    asText(progress.get("message")),
                    asText(progress.get("status")),
                    stage != null ? "处理中: " + stage : "处理中"
            );
            updateTaskProgress(task, percent != null ? percent : 10, message);

            if ("completed".equalsIgnoreCase(stage)) {
                return;
            }
            if ("error".equalsIgnoreCase(stage)) {
                throw new IllegalStateException(firstNonBlank(
                        asText(progress.get("error")),
                        asText(progress.get("message")),
                        "知识库处理失败"
                ));
            }
            Thread.sleep(2000);
        }
    }

    private boolean knowledgeBaseExists(String kbName) {
        return aiServerClient.listKnowledgeBases().stream()
                .map(item -> asText(item.get("name")))
                .filter(Objects::nonNull)
                .anyMatch(kbName::equals);
    }

    private void updateTaskProgress(KbUploadTask task, Integer progressPercent, String statusMessage) {
        task.setProgressPercent(progressPercent);
        task.setStatusMessage(statusMessage);
        taskRepository.save(task);
    }

    private String resolveKbName(KbUploadTask task) {
        if (task.getKbScope() == KbScope.PUBLIC) {
            return runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
        }
        String prefix = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-");
        return prefix + task.getUserId();
    }

    @Transactional
    public void processImmediately(KbUploadTask task) {
        processTask(task);
    }

    @Async
    public void processImmediatelyAsync(KbUploadTask task) {
        try {
            processTask(task);
        } catch (Exception e) {
            log.error("[UploadQueue] Async processing failed: task={}, error={}", task.getId(), e.getMessage());
        }
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/octet-stream";
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void saveAnalysisHistory(KbUploadTask task) {
        try {
            UploadAnalysisHistory history = UploadAnalysisHistory.builder()
                    .userId(task.getUserId())
                    .uploadTaskId(task.getId())
                    .fileName(task.getFileName())
                    .filePath(task.getFilePath())
                    .summary(extractSummary(task.getAnalysisResult()))
                    .mappedKpIds(toJsonArray(task.getMappedKpIds()))
                    .matchedKnowledgePoints(extractMatchedKnowledgePoints(task.getAnalysisResult()))
                    .status(task.getStatus().name())
                    .errorMessage(task.getErrorMessage())
                    .finishedAt(task.getFinishedAt())
                    .build();
            uploadAnalysisHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("[UploadQueue] Failed to save analysis history for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private String extractSummary(String analysisResult) {
        if (analysisResult == null || analysisResult.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(analysisResult);
            String summary = root.path("summary").asText("").trim();
            return summary.isBlank() ? null : summary;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractMatchedKnowledgePoints(String analysisResult) {
        if (analysisResult == null || analysisResult.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(analysisResult);
            com.fasterxml.jackson.databind.JsonNode matchedNode = root.path("matchedKnowledgePoints");
            if (matchedNode.isMissingNode() || matchedNode.isNull()) {
                return null;
            }
            return matchedNode.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toJsonArray(String mappedKpIds) {
        if (mappedKpIds == null || mappedKpIds.isBlank()) {
            return null;
        }
        String[] parts = mappedKpIds.split(",");
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (value.isBlank()) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            json.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            first = false;
        }
        json.append(']');
        return first ? null : json.toString();
    }
}
