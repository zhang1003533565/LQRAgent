package com.lqragent.backend.orchestrator.artifact;

import java.util.Map;

/**
 * 产物结构验证器
 * 提供静态方法供 QualityGate 等使用
 */
public class ArtifactValidator {

    private ArtifactValidator() {
    }

    /**
     * 验证 Artifact 是否包含指定必填字段
     */
    public static boolean validate(Artifact artifact, Map<String, Class<?>> requiredFields) {
        if (artifact == null || artifact.getPayload() == null) return false;
        Map<String, Object> p = artifact.getPayload();
        for (Map.Entry<String, Class<?>> e : requiredFields.entrySet()) {
            Object v = p.get(e.getKey());
            if (v == null) return false;
            if (!e.getValue().isInstance(v)) return false;
        }
        return true;
    }

    /**
     * 媒体类 Artifact 必须包含 url
     */
    public static boolean isMediaValid(Artifact a) {
        if (a == null || a.getPayload() == null) return false;
        String url = String.valueOf(a.getPayload().getOrDefault("url", ""));
        return url.startsWith("http") || url.startsWith("data:");
    }

    /**
     * Quiz Artifact 必须包含非空 questions 数组
     */
    public static boolean isQuizValid(Artifact a) {
        if (a == null || a.getPayload() == null) return false;
        Object q = a.getPayload().get("questions");
        return q instanceof java.util.List<?> list && !list.isEmpty();
    }
}
