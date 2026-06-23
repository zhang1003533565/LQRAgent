package com.lqragent.backend.orchestrator.artifact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从 Agent 步骤数据 / 消息内容中统一提取 Artifact 列表。
 * ChatRouteDispatcher、BaseAgent、测试层共用，避免 agentId.contains() 硬编码。
 */
public final class ArtifactExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ArtifactExtractor() {}

    public static List<Artifact> fromStepData(String producerAgentId, Map<String, Object> stepData) {
        if (stepData == null || stepData.isEmpty()) {
            return List.of();
        }
        String agentId = producerAgentId != null ? producerAgentId : "unknown";
        List<Artifact> artifacts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        collectPrebuilt(stepData, artifacts, seen);
        addFromKindPayload(agentId, stepData, artifacts, seen);
        addRagSources(agentId, stepData, artifacts, seen);
        addFromStructuredFields(agentId, stepData, artifacts, seen);

        Object content = stepData.get("content");
        if (content != null && shouldParseContent(artifacts)) {
            addFromText(agentId, String.valueOf(content), artifacts, seen);
        }
        Object data = stepData.get("data");
        if (data != null && data != content && shouldParseContent(artifacts)) {
            addFromText(agentId, String.valueOf(data), artifacts, seen);
        }

        return List.copyOf(artifacts);
    }

    public static List<Artifact> fromMessageContent(String producerAgentId, Map<String, Object> content) {
        return fromStepData(producerAgentId, content);
    }

    private static void collectPrebuilt(Map<String, Object> stepData, List<Artifact> artifacts, Set<String> seen) {
        Object arts = stepData.get("artifacts");
        if (!(arts instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (o instanceof Artifact a) {
                addIfNew(artifacts, seen, a);
            }
        }
    }

    private static void addFromKindPayload(String agentId, Map<String, Object> stepData,
                                           List<Artifact> artifacts, Set<String> seen) {
        Object kind = stepData.get("artifactKind");
        Object payload = stepData.get("artifactPayload");
        if (kind == null || payload == null) {
            return;
        }
        ArtifactKind ak = ArtifactKind.fromWire(String.valueOf(kind));
        if (hasKind(artifacts, ak)) {
            return;
        }
        Map<String, Object> payloadMap = toPayloadMap(payload);
        addIfNew(artifacts, seen, Artifact.of(ak, agentId, payloadMap));
    }

    /** 已有结构化 artifact 时，避免从 content 重复解析同类型产物 */
    private static boolean shouldParseContent(List<Artifact> artifacts) {
        return !hasKind(artifacts, ArtifactKind.DIAGRAM)
                && !hasKind(artifacts, ArtifactKind.QUIZ)
                && !hasKind(artifacts, ArtifactKind.LEARNING_PATH);
    }

    private static boolean hasKind(List<Artifact> artifacts, ArtifactKind kind) {
        for (Artifact a : artifacts) {
            if (a.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static void addRagSources(String agentId, Map<String, Object> stepData,
                                      List<Artifact> artifacts, Set<String> seen) {
        Object ragSourcesObj = stepData.get("ragSources");
        if (!(ragSourcesObj instanceof List<?> sourcesList) || sourcesList.isEmpty()) {
            return;
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Object item : sourcesList) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) map;
                sources.add(source);
            }
        }
        if (!sources.isEmpty()) {
            addIfNew(artifacts, seen,
                    Artifact.of(ArtifactKind.RAG_SOURCES, agentId, Map.of("sources", sources)));
        }
    }

    private static void addFromStructuredFields(String agentId, Map<String, Object> stepData,
                                                List<Artifact> artifacts, Set<String> seen) {
        String mediaUrl = firstNonBlank(
                stepData.get("mediaUrl"),
                stepData.get("imageUrl"),
                stepData.get("videoUrl"),
                stepData.get("url"));
        if (mediaUrl != null) {
            addMediaArtifact(agentId, mediaUrl, stepData.get("mediaType"), artifacts, seen);
        }
    }

    private static void addFromText(String agentId, String text, List<Artifact> artifacts, Set<String> seen) {
        if (text == null || text.isBlank()) {
            return;
        }
        String trimmed = text.trim();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = MAPPER.readTree(trimmed);
                addFromJsonNode(agentId, root, artifacts, seen);
                return;
            } catch (Exception ignored) {
                // fall through to markdown / plain text
            }
        }

        if (trimmed.contains("```mermaid")) {
            String diagram = extractMermaidBlock(trimmed);
            if (!diagram.isBlank()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("diagram", diagram);
                payload.put("format", "mermaid");
                addIfNew(artifacts, seen, Artifact.of(ArtifactKind.DIAGRAM, agentId, payload));
            }
        }
    }

    private static void addFromJsonNode(String agentId, JsonNode root,
                                        List<Artifact> artifacts, Set<String> seen) {
        if (root == null || root.isNull()) {
            return;
        }

        // quiz: { type: quiz, data: {...} } or { questions: [...] }
        if (root.has("type") && "quiz".equals(root.path("type").asText()) && root.has("data")) {
            Map<String, Object> payload = toPayloadMap(MAPPER.convertValue(root.path("data"), Object.class));
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.QUIZ, agentId, payload));
            return;
        }
        if (root.has("questions")) {
            Map<String, Object> payload = toPayloadMap(MAPPER.convertValue(root, Object.class));
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.QUIZ, agentId, payload));
            return;
        }

        // assessment from grade_answer tool
        if (root.has("score") && (root.has("feedback") || root.has("passed"))) {
            Map<String, Object> payload = toPayloadMap(MAPPER.convertValue(root, Object.class));
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.ASSESSMENT, agentId, payload));
            return;
        }

        // weakness profile from analyze_weakness tool
        if (root.has("weakPoints")) {
            Map<String, Object> payload = toPayloadMap(MAPPER.convertValue(root, Object.class));
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.WEAKNESS_PROFILE, agentId, payload));
            return;
        }

        // recommendation cards
        if (root.has("type") && "recommendation".equals(root.path("type").asText())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (root.has("data") && !root.path("data").isNull()) {
                payload.putAll(toPayloadMap(MAPPER.convertValue(root.path("data"), Object.class)));
            }
            if (root.has("content")) {
                payload.put("content", root.path("content").asText());
            }
            if (root.has("title")) {
                payload.put("title", root.path("title").asText());
            }
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.MULTI_CARD, agentId, payload));
            return;
        }
        if (root.isArray() && !root.isEmpty() && root.get(0).has("title")) {
            Map<String, Object> payload = Map.of("items", MAPPER.convertValue(root, List.class));
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.MULTI_CARD, agentId, payload));
            return;
        }

        // diagram JSON from generate_diagram tool
        if (root.has("diagram")) {
            if (hasKind(artifacts, ArtifactKind.DIAGRAM)) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("diagram", root.path("diagram").asText());
            payload.put("format", root.has("format") ? root.path("format").asText()
                    : root.path("type").asText("mermaid"));
            if (root.has("topic")) {
                payload.put("topic", root.path("topic").asText());
            }
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.DIAGRAM, agentId, payload));
            return;
        }

        // learning path from generate_path tool
        if (root.has("nodes") && root.get("nodes").isArray()) {
            Map<String, Object> payload = toPayloadMap(MAPPER.convertValue(root, Object.class));
            addIfNew(artifacts, seen, Artifact.of(ArtifactKind.LEARNING_PATH, agentId, payload));
            return;
        }

        // nested quiz in data field
        if (root.has("data")) {
            JsonNode dataNode = root.path("data");
            if (dataNode.has("questions") || dataNode.has("title")) {
                Map<String, Object> payload = toPayloadMap(MAPPER.convertValue(dataNode, Object.class));
                addIfNew(artifacts, seen, Artifact.of(ArtifactKind.QUIZ, agentId, payload));
                return;
            }
        }

        // media URL in JSON
        String mediaUrl = firstNonBlankJson(root, "mediaUrl", "imageUrl", "videoUrl", "url");
        if (mediaUrl != null) {
            addMediaArtifact(agentId, mediaUrl, root.path("mediaType").asText(null), artifacts, seen);
        }
    }

    private static void addMediaArtifact(String agentId, String url, Object mediaTypeHint,
                                         List<Artifact> artifacts, Set<String> seen) {
        if (url == null || url.isBlank()) {
            return;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http") && !trimmed.startsWith("data:")) {
            return;
        }
        String mediaType = mediaTypeHint != null ? String.valueOf(mediaTypeHint) : "";
        if (mediaType.isBlank()) {
            mediaType = (trimmed.endsWith(".mp4") || trimmed.endsWith(".webm") || trimmed.endsWith(".mov"))
                    ? "video" : "image";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", trimmed);
        payload.put("mediaType", mediaType);
        ArtifactKind kind = "video".equalsIgnoreCase(mediaType)
                ? ArtifactKind.VIDEO : ArtifactKind.IMAGE;
        addIfNew(artifacts, seen, Artifact.of(kind, agentId, payload));
    }

    private static String extractMermaidBlock(String content) {
        int start = content.indexOf("```mermaid");
        if (start < 0) {
            return content;
        }
        start = content.indexOf('\n', start) + 1;
        int end = content.indexOf("```", start);
        if (end > start) {
            return content.substring(start, end).trim();
        }
        return content.substring(start).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toPayloadMap(Object payload) {
        if (payload instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return Map.of("value", payload);
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String firstNonBlankJson(JsonNode node, String... fields) {
        for (String field : fields) {
            String text = node.path(field).asText("");
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static void addIfNew(List<Artifact> artifacts, Set<String> seen, Artifact artifact) {
        if (artifact == null || artifact.getKind() == null) {
            return;
        }
        if (seen.add(dedupeKey(artifact))) {
            artifacts.add(artifact);
        }
    }

    private static String dedupeKey(Artifact artifact) {
        ArtifactKind kind = artifact.getKind();
        Map<String, Object> payload = artifact.getPayload();
        if (payload == null) {
            return kind.wireCode();
        }
        if (kind == ArtifactKind.DIAGRAM && payload.get("diagram") != null) {
            return "diagram:" + String.valueOf(payload.get("diagram")).trim();
        }
        if (kind == ArtifactKind.QUIZ && payload.containsKey("questions")) {
            return "quiz:" + payload.get("questions").hashCode();
        }
        if (kind == ArtifactKind.RAG_SOURCES && payload.containsKey("sources")) {
            return "rag_sources:" + payload.get("sources").hashCode();
        }
        return kind.wireCode() + ":" + payload.hashCode();
    }
}
