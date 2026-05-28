package com.lqragent.backend.agents.knowledgegraph.repository;

import com.lqragent.backend.agents.knowledgegraph.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByKpId(String kpId);

    boolean existsByKpId(String kpId);
}
