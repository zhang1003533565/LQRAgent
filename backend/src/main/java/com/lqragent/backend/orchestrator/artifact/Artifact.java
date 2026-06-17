package com.lqragent.backend.orchestrator.artifact;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * 统一产物模型（借鉴 A2A Artifact）
 * Agent 间协作 + 后端推送前端，都用这个结构
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Artifact {
    private String artifactId;
    private ArtifactKind kind;
    private String producerAgentId;
    private Map<String, Object> payload;
    private double confidence;
    private Instant createdAt;

    public static Artifact of(ArtifactKind kind, String producerAgentId, Map<String, Object> payload) {
        return Artifact.builder()
                .artifactId(UUID.randomUUID().toString())
                .kind(kind)
                .producerAgentId(producerAgentId)
                .payload(payload)
                .confidence(0.8)
                .createdAt(Instant.now())
                .build();
    }

    public static Artifact ofConfidence(ArtifactKind kind, String producerAgentId,
                                        Map<String, Object> payload, double confidence) {
        return Artifact.builder()
                .artifactId(UUID.randomUUID().toString())
                .kind(kind)
                .producerAgentId(producerAgentId)
                .payload(payload)
                .confidence(confidence)
                .createdAt(Instant.now())
                .build();
    }
}
