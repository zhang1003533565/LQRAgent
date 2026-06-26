package com.lqragent.backend.orchestrator.consultation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lqragent.backend.orchestrator.consultation.entity.AgentConsultationLog;

public interface AgentConsultationLogRepository extends JpaRepository<AgentConsultationLog, Long> {

    List<AgentConsultationLog> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
