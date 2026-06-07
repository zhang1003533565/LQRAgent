package com.lqragent.backend.agents.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 统一响应格式
 * 所有 Agent 工具都应返回此格式
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {
    /** 内容类型：recommendation, knowledge_state, summary, etc. */
    private String type;
    
    /** 标题 */
    private String title;
    
    /** 一句话摘要 */
    private String summary;
    
    /** 主要内容（Markdown 格式） */
    private String content;
    
    /** 结构化数据（可选） */
    private Object data;
    
    /**
     * 创建成功响应
     */
    public static AgentResponse success(String type, String title, String content) {
        return AgentResponse.builder()
                .type(type)
                .title(title)
                .summary(title)
                .content(content)
                .build();
    }
    
    /**
     * 创建带数据的响应
     */
    public static AgentResponse withData(String type, String title, String summary, String content, Object data) {
        return AgentResponse.builder()
                .type(type)
                .title(title)
                .summary(summary)
                .content(content)
                .data(data)
                .build();
    }
    
    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
        }
    }
}
