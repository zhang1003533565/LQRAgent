package com.lqragent.backend.orchestrator.message;

/**
 * 消息语义类型（参考 FIPA ACL 规范）
 */
public enum Performative {
    REQUEST,   // 请求执行任务
    INFORM,    // 返回结果
    PROGRESS,  // 进度通知
    ERROR      // 执行失败
}
