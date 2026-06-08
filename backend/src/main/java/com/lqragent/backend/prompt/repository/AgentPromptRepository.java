package com.lqragent.backend.prompt.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lqragent.backend.prompt.entity.AgentPrompt;

@Repository
public interface AgentPromptRepository extends JpaRepository<AgentPrompt, Long> {

    Optional<AgentPrompt> findByAgentId(String agentId);

    boolean existsByAgentId(String agentId);
}
