package com.lqragent.backend.uploadqueue.service;

import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.agents.content_analyzer.service.ContentAnalyzerService;
import com.lqragent.backend.storage.QiniuStorageService;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.KbScope;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.TaskStatus;
import com.lqragent.backend.uploadqueue.repository.KbUploadTaskRepository;
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

/**
 * 上传任务队列服务。
 * - enqueue()：接收上传请求，落库返回 PENDING 状态
 * - processNext()：定时轮询，批量取 PENDING 任务处理（30 秒一次，最多 5 条）
 * - scope 路由：PUBLIC → kb-public，PERSONAL → kb-private-{userId}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadQueueService {

    private final KbUploadTaskRepository taskRepository;
    private final AiServerClient aiServerClient;
    private final ContentAnalyzerService contentAnalyzerService;
    private final AppRuntimeConfig runtimeConfig;
    private final QiniuStorageService qiniuStorageService;

    private static final int BATCH_SIZE = 5;

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

    public KbUploadTask getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("上传任务不存在: " + id));
    }

    @Transactional
    public void deleteTask(Long id) {
        KbUploadTask task = taskRepository.findById(id).orElse(null);
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
     * 定时轮询：每 30 秒批量取最多 5 条 PENDING 任务处理。
     */
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

    /**
     * 处理单条任务：按 scope 路由到不同 KB。
     */
    private void processTask(KbUploadTask task) {
        log.info("[UploadQueue] Processing task id={}, file={}, scope={}",
                task.getId(), task.getFileName(), task.getKbScope());
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        try {
            // 1. 根据 scope 确定 KB 名称
            String kbName = resolveKbName(task);
            try {
                aiServerClient.createKnowledgeBase(kbName);
            } catch (Exception e) {
                log.warn("[UploadQueue] KB may already exist: {}", e.getMessage());
            }

            // 2. 从七牛云下载文件，发送到 ai-server
            byte[] content = qiniuStorageService.download(task.getFilePath());
            String mimeType = detectMimeType(task.getFileName());
            try {
                aiServerClient.uploadDocument(kbName, task.getFileName(), content, mimeType);
                log.info("[UploadQueue] Uploaded to KB '{}': {}", kbName, task.getFileName());
            } catch (Exception e) {
                log.warn("[UploadQueue] ai-server upload failed (non-fatal): {}", e.getMessage());
            }

            // 3. 内容分析 → 映射知识点（公共库也做，方便管理后台查看）
            var analysis = contentAnalyzerService.analyze(task.getFilePath(), task.getFileName());
            task.setAnalysisResult(analysis.toJson());
            task.setMappedKpIds(String.join(",", analysis.mappedKpIds()));

            task.setStatus(TaskStatus.COMPLETED);
            log.info("[UploadQueue] Task {} completed, KB={}, mapped KPs: {}",
                    task.getId(), kbName, analysis.mappedKpIds());
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            log.error("[UploadQueue] Task {} failed: {}", task.getId(), e.getMessage());
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    /**
     * 根据 scope 决定 KB 名称。
     * PUBLIC → kb-public（公共资料库，所有用户共享）
     * PERSONAL → kb-private-{userId}（用户私有知识库）
     */
    private String resolveKbName(KbUploadTask task) {
        if (task.getKbScope() == KbScope.PUBLIC) {
            return runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
        }
        String prefix = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-");
        return prefix + task.getUserId();
    }

    /**
     * 立即处理指定任务（同步，阻塞）。
     */
    @Transactional
    public void processImmediately(KbUploadTask task) {
        processTask(task);
    }

    /**
     * 异步处理指定任务（不阻塞 HTTP 响应）。
     */
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
        return "application/octet-stream";
    }
}
