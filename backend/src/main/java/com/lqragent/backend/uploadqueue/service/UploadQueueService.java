package com.lqragent.backend.uploadqueue.service;

import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.agents.content_analyzer.service.ContentAnalyzerService;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.KbScope;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.TaskStatus;
import com.lqragent.backend.uploadqueue.repository.KbUploadTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传任务队列服务。
 * - enqueue()：接收上传请求，落库返回 PENDING 状态
 * - processNext()：定时轮询，取一条 PENDING 任务处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadQueueService {

    private final KbUploadTaskRepository taskRepository;
    private final AiServerClient aiServerClient;
    private final ContentAnalyzerService contentAnalyzerService;

    /**
     * 将上传任务入队，立即返回，不阻塞前端。
     */
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

    /**
     * 查询某用户的所有上传任务（按时间倒序）。
     */
    public List<KbUploadTask> listByUser(Long userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<KbUploadTask> listRecent(int limit) {
        return taskRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
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

    /**
     * 定时轮询：每隔 ${upload.queue.worker-interval-ms} 毫秒取一条 PENDING 任务处理。
     */
    @Scheduled(fixedDelayString = "${upload.queue.worker-interval-ms:5000}")
    @Transactional
    public void processNext() {
        taskRepository.findFirstByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus.PENDING)
                .ifPresent(this::processTask);
    }

    private void processTask(KbUploadTask task) {
        log.info("[UploadQueue] Processing task id={}, file={}", task.getId(), task.getFileName());
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        try {
            // 1. 创建/获取 DeepTutor 知识库
            String kbName = "lqragent-uploads";
            try {
                aiServerClient.createKnowledgeBase(kbName);
            } catch (Exception e) {
                log.warn("[UploadQueue] KB may already exist: {}", e.getMessage());
            }

            // 2. 读取文件并上传到 DeepTutor
            Path filePath = Path.of(task.getFilePath());
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                byte[] content = Files.readAllBytes(filePath);
                String mimeType = detectMimeType(task.getFileName());
                try {
                    aiServerClient.uploadDocument(kbName, task.getFileName(), content, mimeType);
                    log.info("[UploadQueue] Uploaded to DeepTutor KB: {}", task.getFileName());
                } catch (Exception e) {
                    log.warn("[UploadQueue] DeepTutor upload failed (non-fatal): {}", e.getMessage());
                }
            }

            // 3. 内容分析 → 映射知识点
            var analysis = contentAnalyzerService.analyze(task.getFilePath(), task.getFileName());
            task.setAnalysisResult(analysis.toJson());
            task.setMappedKpIds(String.join(",", analysis.mappedKpIds()));

            task.setStatus(TaskStatus.COMPLETED);
            log.info("[UploadQueue] Task {} completed, mapped KPs: {}", task.getId(), analysis.mappedKpIds());
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            log.error("[UploadQueue] Task {} failed: {}", task.getId(), e.getMessage());
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
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
        return "application/octet-stream";
    }
}
