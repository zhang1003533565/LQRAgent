package com.lqragent.backend.agents.learn.path.repository;

import com.lqragent.backend.agents.learn.path.entity.LearningPathStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningPathStepRepository extends JpaRepository<LearningPathStep, Long> {

    List<LearningPathStep> findByPathIdOrderByStepOrder(Long pathId);

    Optional<LearningPathStep> findByPathIdAndKpId(Long pathId, String kpId);
}
