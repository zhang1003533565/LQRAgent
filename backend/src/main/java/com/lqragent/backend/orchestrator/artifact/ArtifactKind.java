package com.lqragent.backend.orchestrator.artifact;

/**
 * 统一产物类型枚举
 * 与前端 frontend/src/utils/types/artifact.ts 的 ArtifactKind 对齐
 */
public enum ArtifactKind {
    TEXT("text"),
    QUIZ("quiz"),
    IMAGE("media_image"),
    VIDEO("video"),
    DIAGRAM("diagram"),
    LEARNING_PATH("learning_path"),
    RAG_SOURCES("rag_sources"),
    PROFILE("profile"),
    ASSESSMENT("assessment"),
    SUMMARY("summary"),
    WEAKNESS_PROFILE("weakness_profile"),
    MULTI_CARD("multi_card");

    private final String wireCode;

    ArtifactKind(String wireCode) {
        this.wireCode = wireCode;
    }

    public String wireCode() {
        return wireCode;
    }

    /**
     * 从前端兼容字段反推
     */
    public static ArtifactKind fromWire(String code) {
        if (code == null) return TEXT;
        for (ArtifactKind k : values()) {
            if (k.wireCode.equalsIgnoreCase(code)) return k;
        }
        // 兼容前端旧字段 media_video → VIDEO
        if ("media_video".equalsIgnoreCase(code)) return VIDEO;
        return TEXT;
    }
}
