package com.lqragent.backend.orchestrator.message;

/**
 * 消息语义类型（参考 FIPA ACL 规范）
 */
public enum Performative {
    REQUEST,    // 请求执行任务
    INFORM,     // 返回结果
    PROGRESS,   // 进度通知
    ERROR,      // 执行失败
    NEGOTIATE,  // 协商：Agent 间协作决策
    REFUSE,     // 拒绝：Agent 无法完成任务
    CFP,        // Call for Proposal：招标式任务分配
    PROPOSE,    // 提案：响应 CFP
    CONFIRM,     // 确认：任务完成确认
    REQUEST_PEER, // Agent间协作请求
    INFORM_PEER   // Agent间协作响应
}
