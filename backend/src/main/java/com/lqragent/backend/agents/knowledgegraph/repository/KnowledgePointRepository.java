package com.lqragent.backend.agents.knowledgegraph.repository;

import com.lqragent.backend.agents.knowledgegraph.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByKpId(String kpId);

    boolean existsByKpId(String kpId);

    List<KnowledgePoint> findBySubject(String subject);

    @Query("SELECT DISTINCT kp.subject FROM KnowledgePoint kp WHERE kp.subject IS NOT NULL ORDER BY kp.subject")
    List<String> findDistinctSubjects();
}
