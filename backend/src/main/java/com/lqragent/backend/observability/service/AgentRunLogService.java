package com.lqragent.backend.observability.service;

import com.lqragent.backend.observability.entity.AgentRunLog;
import com.lqragent.backend.observability.repository.AgentRunLogRepository;
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
}
