package com.lqragent.backend.uploadqueue.service;

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
            // TODO: 调用 AiServerClient.uploadDocument() 将文件送入 ai-server 知识库
            // aiServerClient.uploadDocument(kbName, task.getFileName(), Files.readAllBytes(...));

            task.setStatus(TaskStatus.COMPLETED);
            log.info("[UploadQueue] Task {} completed", task.getId());
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            log.error("[UploadQueue] Task {} failed: {}", task.getId(), e.getMessage());
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }
}
