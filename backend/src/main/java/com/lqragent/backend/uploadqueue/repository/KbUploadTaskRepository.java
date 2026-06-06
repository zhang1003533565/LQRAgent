package com.lqragent.backend.uploadqueue.repository;

import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbUploadTaskRepository extends JpaRepository<KbUploadTask, Long> {

    List<KbUploadTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KbUploadTask> findByIdAndUserId(Long id, Long userId);

    List<KbUploadTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(TaskStatus status);

    Optional<KbUploadTask> findFirstByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status);

    List<KbUploadTask> findTopNByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status, Pageable pageable);
}
