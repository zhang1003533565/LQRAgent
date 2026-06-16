package com.lqragent.backend.orchestrator.pipeline.repository;

import com.lqragent.backend.orchestrator.pipeline.entity.PipelineTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineTaskRepository extends JpaRepository<PipelineTask, Long> {

    Optional<PipelineTask> findByTaskId(String taskId);

    List<PipelineTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PipelineTask> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    Optional<PipelineTask> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
