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
import com.lqragent.backend.uploadqueue.entity.VectorChunk;
import com.lqragent.backend.uploadqueue.repository.KbUploadTaskRepository;
import com.lqragent.backend.uploadqueue.repository.UploadAnalysisHistoryRepository;
import com.lqragent.backend.uploadqueue.repository.VectorChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    private final VectorChunkRepository vectorChunkRepository;
    private final AiServerClient aiServerClient;
    private final ContentAnalyzerService contentAnalyzerService;
    private final AppRuntimeConfig runtimeConfig;
    private final QiniuStorageService qiniuStorageService;

    @Transactional
    public KbUploadTask enqueue(Long userId, String fileName, String filePath, KbScope scope) {
        String normalizedFileName = normalizeFileName(fileName);
        // 检查是否有同名文件正在处理中
        if (taskRepository.existsByUserIdAndFileNameAndStatus(userId, normalizedFileName, TaskStatus.PROCESSING)) {
            log.info("[UploadQueue] File is already processing: userId={}, fileName={}", userId, normalizedFileName);
            return taskRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(t -> normalizedFileName.equals(t.getFileName()) && t.getStatus() == TaskStatus.PROCESSING)
                    .findFirst()
                    .orElse(createNewTask(userId, normalizedFileName, filePath, scope));
        }
        
        // 检查是否已经有同名文件的上传记录（状态为已完成）
        if (taskRepository.existsByUserIdAndFileNameAndStatus(userId, normalizedFileName, TaskStatus.COMPLETED)) {
            // 检查知识库中是否仍然存在该文件
            // 如果知识库中已不存在，则允许重新上传
            String kbName = scope == KbScope.PUBLIC 
                    ? runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public")
                    : runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-") + userId;
            
            if (isFileInKnowledgeBase(kbName, normalizedFileName)) {
                // 文件仍在知识库中，返回已存在的记录作为上传历史
                log.info("[UploadQueue] File already exists in knowledge base: userId={}, fileName={}, kb={}", userId, normalizedFileName, kbName);
                return taskRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(t -> normalizedFileName.equals(t.getFileName()) && t.getStatus() == TaskStatus.COMPLETED)
                        .findFirst()
                        .orElse(createNewTask(userId, normalizedFileName, filePath, scope));
            } else {
                // 文件已从知识库删除，允许重新上传
                log.info("[UploadQueue] File deleted from knowledge base, allowing re-upload: userId={}, fileName={}", userId, normalizedFileName);
            }
        }
        
        // 创建新的上传任务
        return createNewTask(userId, normalizedFileName, filePath, scope);
    }
    
    /**
     * 检查文件是否存在于知识库中
     */
    private boolean isFileInKnowledgeBase(String kbName, String fileName) {
        try {
            List<Map<String, Object>> documents = aiServerClient.listDocuments(kbName);
            if (documents == null) {
                return false;
            }
            return documents.stream()
                    .map(doc -> asText(doc.get("name")))
                    .filter(Objects::nonNull)
                    .anyMatch(name -> fileName.equalsIgnoreCase(name) || name != null && name.contains(fileName));
        } catch (Exception e) {
            log.warn("[UploadQueue] Failed to check if file exists in KB: {}", e.getMessage());
            return false;
        }
    }
    
    private KbUploadTask createNewTask(Long userId, String fileName, String filePath, KbScope scope) {
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

            Map<String, Object> submitResponse;
            try {
                if (kbExists) {
                    submitResponse = aiServerClient.uploadDocument(kbName, task.getFileName(), content, mimeType);
                } else {
                    submitResponse = aiServerClient.createKnowledgeBase(kbName, task.getFileName(), content, mimeType);
                }
            } catch (Exception e) {
                // 如果上传失败且原因是知识库未初始化，则尝试创建（重新初始化）知识库
                if (e.getMessage() != null && e.getMessage().contains("Knowledge base not initialized")) {
                    log.warn("[UploadQueue] KB not initialized, creating new: {}", kbName);
                    submitResponse = aiServerClient.createKnowledgeBase(kbName, task.getFileName(), content, mimeType);
                } else {
                    throw e;
                }
            }

            String aiTaskId = asText(submitResponse.get("task_id"));
            waitForKnowledgeProcessing(task, kbName, aiTaskId, content, mimeType);

            updateTaskProgress(task, 85, "正在获取向量块数据");
            // 获取并保存向量块
            saveVectorChunks(task, kbName);
            
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
        waitForKnowledgeProcessing(task, kbName, expectedTaskId, null, null);
    }
    
    private void waitForKnowledgeProcessing(KbUploadTask task, String kbName, String expectedTaskId, 
                                            byte[] content, String mimeType) throws InterruptedException {
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
                String errorMessage = firstNonBlank(
                        asText(progress.get("error")),
                        asText(progress.get("message")),
                        "知识库处理失败"
                );
                // 如果错误是知识库未初始化，尝试先删除再重新创建知识库
                if (errorMessage.contains("Knowledge base not initialized") && content != null && mimeType != null) {
                    log.warn("[UploadQueue] KB processing failed due to uninitialized KB, deleting and recreating: {}", kbName);
                    updateTaskProgress(task, 15, "知识库未初始化，正在删除并重新创建");
                    // 先删除已存在但未初始化的知识库
                    aiServerClient.deleteKnowledgeBase(kbName);
                    // 等待删除完成
                    Thread.sleep(1000);
                    // 创建新的知识库
                    Map<String, Object> createResponse = aiServerClient.createKnowledgeBase(kbName, task.getFileName(), content, mimeType);
                    expectedTaskId = asText(createResponse.get("task_id"));
                    Thread.sleep(2000);
                    continue;
                }
                throw new IllegalStateException(errorMessage);
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
    
    /**
     * 获取并保存向量块数据
     */
    private void saveVectorChunks(KbUploadTask task, String kbName) {
        log.info("[UploadQueue] saveVectorChunks started: taskId={}, kbName={}, fileName={}", 
                task.getId(), kbName, task.getFileName());
        
        try {
            log.info("[UploadQueue] Calling aiServerClient.getDocumentChunks: kb={}, file={}", kbName, task.getFileName());
            List<Map<String, Object>> chunks = aiServerClient.getDocumentChunks(kbName, task.getFileName());
            log.info("[UploadQueue] Got {} chunks from ai-server for task {}", chunks.size(), task.getId());
            
            if (chunks.isEmpty()) {
                log.warn("[UploadQueue] No chunks returned from ai-server for task {}", task.getId());
                return;
            }
            
            int chunkCount = 0;
            long tokenCount = 0;
            
            for (Map<String, Object> chunkData : chunks) {
                Integer index = asInteger(chunkData.get("chunk_index"));
                String content = asText(chunkData.get("content"));
                Integer tokens = asInteger(chunkData.get("token_count"));
                String metadata = asText(chunkData.get("metadata"));
                String kpId = asText(chunkData.get("kp_id"));
                String indexName = asText(chunkData.get("index_name"));
                
                log.debug("[UploadQueue] Processing chunk: index={}, contentLength={}, tokens={}", 
                        index, content != null ? content.length() : 0, tokens);
                
                if (content != null && !content.isBlank()) {
                    VectorChunk chunk = VectorChunk.builder()
                            .taskId(task.getId())
                            .indexName(indexName != null ? indexName : kbName)
                            .chunkIndex(index != null ? index : chunkCount)
                            .content(content)
                            .tokenCount(tokens != null ? tokens : 0)
                            .metadata(metadata)
                            .kpId(kpId)
                            .build();
                    
                    VectorChunk saved = vectorChunkRepository.save(chunk);
                    log.debug("[UploadQueue] Saved chunk: id={}, chunkIndex={}", saved.getId(), saved.getChunkIndex());
                    chunkCount++;
                    tokenCount += tokens != null ? tokens : 0;
                } else {
                    log.warn("[UploadQueue] Skipping empty chunk at index {}", index);
                }
            }
            
            // 更新任务统计信息
            task.setVectorChunkCount(chunkCount);
            task.setVectorTotalTokens(tokenCount);
            log.info("[UploadQueue] Saved {} vector chunks, total tokens: {}", chunkCount, tokenCount);
            
        } catch (Exception e) {
            log.error("[UploadQueue] Failed to save vector chunks for task {}: {}", task.getId(), e.getMessage(), e);
            // 不抛出异常，继续处理
        }
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

    private String normalizeFileName(String fileName) {
        if (fileName == null || !looksLikeMojibake(fileName)) {
            return fileName;
        }
        for (var charset : List.of(StandardCharsets.ISO_8859_1, java.nio.charset.Charset.forName("windows-1252"))) {
            try {
                String decoded = new String(fileName.getBytes(charset), StandardCharsets.UTF_8);
                if (!decoded.contains("�")) {
                    return decoded;
                }
            } catch (Exception ignored) {
            }
        }
        return fileName;
    }

    private boolean looksLikeMojibake(String value) {
        return value.indexOf('Ã') >= 0 || value.indexOf('Â') >= 0 || value.indexOf('æ') >= 0
                || value.indexOf('ç') >= 0 || value.indexOf('è') >= 0 || value.indexOf('å') >= 0;
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
