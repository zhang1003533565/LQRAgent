package com.lqragent.backend.orchestrator.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.artifact.Artifact;
import com.lqragent.backend.orchestrator.artifact.ArtifactKind;

/**
 * 决定 Pipeline 步骤是否向用户推送文本 chunk / 参与结果聚合。
 * 替代各处的 agentId.contains() 硬编码。
 */
public final class StepStreamPolicy {

    /** 仅内部加工、不应作为最终回答展示的 Agent */
    private static final Set<String> INTERNAL_ONLY = Set.of(
            AgentIds.PROFILE,
            AgentIds.CONTENT_ANALYSIS,
            AgentIds.QUALITY,
            AgentIds.EFFECT,
            AgentIds.KNOWLEDGE_STATE,
            AgentIds.DIFFICULTY,
            AgentIds.LEARNING_STYLE,
            AgentIds.PROMPT_GEN
    );

    /** 产物由 Artifact 卡片渲染，不再重复推送状态文本 */
    private static final Set<String> ARTIFACT_RENDERED = Set.of(
            AgentIds.MEDIA_GEN,
            AgentIds.DIAGRAM
    );

    private StepStreamPolicy() {}

    public static boolean isInternalStep(String agentId) {
        return agentId != null && INTERNAL_ONLY.contains(agentId);
    }

    public static boolean isArtifactRenderedStep(String agentId) {
        return agentId != null && ARTIFACT_RENDERED.contains(agentId);
    }

    public static boolean isStatusOnlyText(String content) {
        return isStatusOnlyTextInternal(content);
    }

    public static boolean shouldStreamContent(String agentId, Map<String, Object> stepData,
                                              List<Artifact> artifacts) {
        if (agentId == null || stepData == null) {
            return false;
        }
        if (INTERNAL_ONLY.contains(agentId)) {
            return false;
        }
        if (ARTIFACT_RENDERED.contains(agentId)) {
            return false;
        }
        if (AgentIds.QA.equals(agentId) && isIntermediateQaStep(stepData)) {
            return false;
        }
        if (hasUserFacingArtifact(artifacts)) {
            String content = stringField(stepData, "content");
            if (content == null || content.isBlank() || isStatusOnlyTextInternal(content)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIntermediateQaStep(Map<String, Object> stepData) {
        Object toolCalls = stepData.get("toolCalls");
        if (toolCalls instanceof Number n && n.intValue() > 0) {
            String content = stringField(stepData, "content");
            if (content == null || content.length() < 120) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUserFacingArtifact(List<Artifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return false;
        }
        for (Artifact a : artifacts) {
            ArtifactKind kind = a.getKind();
            if (kind == ArtifactKind.QUIZ
                    || kind == ArtifactKind.IMAGE
                    || kind == ArtifactKind.VIDEO
                    || kind == ArtifactKind.DIAGRAM
                    || kind == ArtifactKind.LEARNING_PATH
                    || kind == ArtifactKind.MULTI_CARD) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStatusOnlyTextInternal(String content) {
        String trimmed = content.trim();
        return trimmed.length() < 80
                && (trimmed.contains("已生成") || trimmed.contains("生成完成") || trimmed.startsWith("artifact:"));
    }

    private static String stringField(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
