package com.lqragent.backend.systemconfig;

/**
 * sys_config 与 application.properties 对齐的配置键。
 */
public final class ConfigKeys {

    private ConfigKeys() {}

    public static final String AI_SERVER_BASE_URL = "ai-server.base-url";
    public static final String AI_SERVER_WS_URL = "ai-server.ws-url";
    public static final String AI_SERVER_AUTO_START = "ai-server.auto-start";

    public static final String UPLOAD_WORKER_INTERVAL_MS = "upload.queue.worker-interval-ms";
    public static final String UPLOAD_MAX_CONCURRENT = "upload.queue.max-concurrent";

    public static final String JWT_EXPIRATION_MS = "jwt.expiration-ms";

    public static final String SERVER_PORT = "server.port";
    public static final String DATASOURCE_URL = "spring.datasource.url";

    /** 大模型对话 API（对应 ai-server .env 中 LLM_*） */
    public static final String LLM_BINDING = "llm.binding";
    public static final String LLM_MODEL = "llm.model";
    public static final String LLM_API_KEY = "llm.api-key";
    public static final String LLM_HOST = "llm.host";
    public static final String LLM_API_VERSION = "llm.api-version";

    /** 向量嵌入 API（对应 ai-server .env 中 EMBEDDING_*，知识库/RAG 需要） */
    public static final String EMBEDDING_BINDING = "embedding.binding";
    public static final String EMBEDDING_MODEL = "embedding.model";
    public static final String EMBEDDING_API_KEY = "embedding.api-key";
    public static final String EMBEDDING_HOST = "embedding.host";

    /** 视频生成 API（对应 ai-server .env 中 VIDEO_*） */
    public static final String VIDEO_BINDING = "video.binding";
    public static final String VIDEO_MODEL = "video.model";
    public static final String VIDEO_API_KEY = "video.api-key";
    public static final String VIDEO_HOST = "video.host";

    /** RAG 知识库名称（ai-server 侧的知识库标识） */
    public static final String RAG_KB_NAME = "agent.rag.kb_name";

    /** 公共知识库名称 */
    public static final String KB_PUBLIC = "agent.rag.kb_public";
    /** 私有知识库前缀（拼 userId） */
    public static final String KB_PRIVATE_PREFIX = "agent.rag.kb_private_prefix";

    /** 七牛云对象存储 */
    public static final String QINIU_ACCESS_KEY = "qiniu.access-key";
    public static final String QINIU_SECRET_KEY = "qiniu.secret-key";
    public static final String QINIU_BUCKET = "qiniu.bucket";
    public static final String QINIU_REGION = "qiniu.region";

    /** 图片生成 API（对应 ai-server .env 中 IMAGE_*） */
    public static final String IMAGE_BINDING = "image.binding";
    public static final String IMAGE_MODEL = "image.model";
    public static final String IMAGE_API_KEY = "image.api-key";
    public static final String IMAGE_HOST = "image.host";

}
