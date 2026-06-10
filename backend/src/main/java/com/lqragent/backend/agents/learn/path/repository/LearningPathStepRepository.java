package com.lqragent.backend.agents.learn.path.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lqragent.backend.agents.learn.path.entity.LearningPathStep;

public interface LearningPathStepRepository extends JpaRepository<LearningPathStep, Long> {

    List<LearningPathStep> findByPathIdOrderByStepOrder(Long pathId);

    Optional<LearningPathStep> findByPathIdAndKpId(Long pathId, String kpId);
}
