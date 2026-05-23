package com.lqragent.backend.observability.repository;

import com.lqragent.backend.observability.entity.AgentRunLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunLogRepository extends JpaRepository<AgentRunLog, Long> {

    List<AgentRunLog> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    Page<AgentRunLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AgentRunLog> findByAgentAndStatusAndCreatedAtBetween(
            String agent, AgentRunLog.RunStatus status,
            java.time.LocalDateTime start, java.time.LocalDateTime end);
}
