package com.lqragent.backend.framework;

/**
 * 请求上下文 — 全链路传递 userId + requestId。
 * 入口 Filter / WS Handler 设置，所有 Agent 和 Service 通过 get() 获取。
 * 请求结束时必须 clear() 防止 ThreadLocal 泄漏。
 */
public class RequestContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    public static void init(Long userId) {
        USER_ID.set(userId);
        REQUEST_ID.set(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    public static void init(Long userId, String requestId) {
        USER_ID.set(userId);
        REQUEST_ID.set(requestId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
        REQUEST_ID.remove();
    }
}
