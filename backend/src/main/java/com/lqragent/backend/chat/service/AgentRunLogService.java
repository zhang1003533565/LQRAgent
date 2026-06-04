package com.lqragent.backend.chat.service;

import com.lqragent.backend.chat.entity.AgentRunLog;
import com.lqragent.backend.admin.repository.AgentRunLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunLogService {

    private final AgentRunLogRepository agentRunLogRepository;

    @Transactional
    public AgentRunLog startRun(String sessionId, Long userId, String agent, String intent) {
        AgentRunLog logEntry = AgentRunLog.builder()
                .sessionId(sessionId)
                .userId(userId)
                .agent(agent)
                .intent(intent)
                .status(AgentRunLog.RunStatus.RUNNING)
                .build();
        AgentRunLog saved = agentRunLogRepository.save(logEntry);
        log.info("[AgentRunLog] start: id={}, agent={}, sessionId={}", saved.getId(), agent, sessionId);
        return saved;
    }

    @Transactional
    public void completeRun(Long logId, int durationMs, String detail) {
        agentRunLogRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(AgentRunLog.RunStatus.SUCCESS);
            logEntry.setDurationMs(durationMs);
            logEntry.setDetail(detail);
            agentRunLogRepository.save(logEntry);
            log.info("[AgentRunLog] complete: id={}, agent={}, durationMs={}", logId, logEntry.getAgent(), durationMs);
        });
    }

    @Transactional
    public void failRun(Long logId, String errorMessage) {
        agentRunLogRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(AgentRunLog.RunStatus.FAILED);
            logEntry.setErrorMessage(errorMessage);
            agentRunLogRepository.save(logEntry);
            log.info("[AgentRunLog] fail: id={}, agent={}, error={}", logId, logEntry.getAgent(), errorMessage);
        });
}

    /**
     * 简化版记录调用（Orchestrator 使用）
     */
    @Transactional
    public void recordCall(String agentName, boolean success, long durationMs) {
        recordCall(agentName, success, durationMs, null);
    }

    public void recordCall(String agentName, boolean success, long durationMs, String errorMessage) {
        try {
            AgentRunLog logEntry = AgentRunLog.builder()
                    .sessionId("orchestrator")
                    .userId(0L)
                    .agent(agentName)
                    .intent("orchestrator_task")
                    .status(success ? AgentRunLog.RunStatus.SUCCESS : AgentRunLog.RunStatus.FAILED)
                    .durationMs((int) durationMs)
                    .errorMessage(errorMessage)
                    .build();
            agentRunLogRepository.save(logEntry);
            log.info("[AgentRunLog] recorded: agent={}, success={}, error={}", agentName, success, errorMessage);
        } catch (Exception e) {
            log.warn("[AgentRunLog] recordCall failed: {}", e.getMessage());
        }
    }

}