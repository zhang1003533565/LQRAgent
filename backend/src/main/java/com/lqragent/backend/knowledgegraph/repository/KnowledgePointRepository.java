package com.lqragent.backend.knowledgegraph.repository;

import com.lqragent.backend.knowledgegraph.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByKpId(String kpId);

    boolean existsByKpId(String kpId);
}
