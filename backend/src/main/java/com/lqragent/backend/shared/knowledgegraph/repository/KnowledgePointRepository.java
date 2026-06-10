package com.lqragent.backend.shared.knowledgegraph.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    Optional<KnowledgePoint> findByKpId(String kpId);

    boolean existsByKpId(String kpId);

    List<KnowledgePoint> findByKpIdIn(List<String> kpIds);

    List<KnowledgePoint> findBySubject(String subject);

    @Query("SELECT DISTINCT kp.subject FROM KnowledgePoint kp WHERE kp.subject IS NOT NULL ORDER BY kp.subject")
    List<String> findDistinctSubjects();

    List<KnowledgePoint> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String titleKeyword, String descKeyword);
}
