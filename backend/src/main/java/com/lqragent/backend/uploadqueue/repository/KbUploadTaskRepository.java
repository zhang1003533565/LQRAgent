package com.lqragent.backend.uploadqueue.repository;

import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KbUploadTaskRepository extends JpaRepository<KbUploadTask, Long> {

    List<KbUploadTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KbUploadTask> findByIdAndUserId(Long id, Long userId);

    List<KbUploadTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(TaskStatus status);

    Optional<KbUploadTask> findFirstByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status);

    List<KbUploadTask> findTopNByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status, Pageable pageable);

    /**
     * 检查用户是否已经上传过同名文件
     */
    boolean existsByUserIdAndFileName(Long userId, String fileName);

    /**
     * 检查用户是否已经上传过同名文件且状态为已完成
     */
    boolean existsByUserIdAndFileNameAndStatus(Long userId, String fileName, TaskStatus status);

    long countByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(t.fileSizeBytes), 0) FROM KbUploadTask t WHERE t.userId = :userId")
    long sumFileSizeBytesByUserId(@Param("userId") Long userId);
}
