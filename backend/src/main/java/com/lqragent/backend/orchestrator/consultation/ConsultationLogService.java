package com.lqragent.backend.orchestrator.consultation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.consultation.entity.AgentConsultationLog;
import com.lqragent.backend.orchestrator.consultation.repository.AgentConsultationLogRepository;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationLogService {

    private final AppRuntimeConfig runtimeConfig;
    private final AgentConsultationLogRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void persistIfEnabled(TaskContext context, ConsultationTranscript transcript) {
        if (!runtimeConfig.isSupervisorPersistTranscript() || context == null || transcript == null) {
            return;
        }
        try {
            String sessionId = resolveSessionId(context);
            Long userId = parseUserId(context.getUserId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("scene", transcript.scene().name());
            body.put("participants", transcript.participants());
            body.put("rounds", transcript.rounds());
            body.put("stopReason", transcript.stopReason() != null ? transcript.stopReason().name() : null);
            body.put("durationMs", transcript.durationMs());

            AgentConsultationLog row = AgentConsultationLog.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .scene(transcript.scene().name())
                    .stopReason(transcript.stopReason() != null ? transcript.stopReason().name() : null)
                    .durationMs((int) transcript.durationMs())
                    .transcriptJson(objectMapper.writeValueAsString(body))
                    .build();
            repository.save(row);
            context.put("consultation.logId", row.getId());
        } catch (Exception e) {
            log.warn("[ConsultationLog] persist failed: {}", e.getMessage());
        }
    }

    public List<AgentConsultationLog> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return repository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    public Optional<AgentConsultationLog> findLatestBySessionId(String sessionId) {
        List<AgentConsultationLog> rows = findBySessionId(sessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private String resolveSessionId(TaskContext context) {
        Object fromShared = context.get("chat.sessionId");
        if (fromShared != null && !String.valueOf(fromShared).isBlank()) {
            return String.valueOf(fromShared);
        }
        if (context.getSessionId() != null && !context.getSessionId().isBlank()) {
            return context.getSessionId();
        }
        return context.getTaskId();
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
