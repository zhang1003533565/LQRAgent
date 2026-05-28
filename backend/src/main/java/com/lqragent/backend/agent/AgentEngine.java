package com.lqragent.backend.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能体推理引擎 — 所有 Agent 共用的 LLM 推理循环。
 * <p>
 * 核心逻辑：
 * <pre>
 *   messages = [systemPrompt, userInput]
 *   while true:
 *     response = llm.reason(messages, tools=agent.getTools())
 *     if response.isFinal():  return response.content
 *     for toolCall in response.toolCalls:
 *       result = registry.execute(toolCall)
 *       messages.add(shortSummary(result))   ← 截断，防 Token 放大
 * </pre>
 * </p>
 *
 * <h3>并发隔离</h3>
 * Prototype scope：每个请求一个新实例。
 * 多轮推理通过 {@link SessionContext} 按 sessionId 隔离消息列表。
 *
 * <h3>长短上下文分离</h3>
 * Tool 执行结果不返回完整内容（如数千字的讲义），
 * 只返回 {@code {"resourceId": 123, "status": "success"}} 简短 JSON，
 * 完整内容直接写入 DB，不喂回 LLM。
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class AgentEngine {

    private final AppRuntimeConfig runtimeConfig;
    private final ToolRegistry toolRegistry;

    private static final int MAX_ROUNDS = 10;
    /** Tool 结果返回给 LLM 的最大长度（防 Token 放大） */
    private static final int MAX_TOOL_RESULT_LENGTH = 500;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 运行 Agent 推理循环。
     *
     * @param agent     目标 Agent
     * @param task      用户任务
     * @param sessionId 会话 ID（用于隔离多轮推理上下文；传空则每次新开）
     * @param extraContext 额外上下文（可选）
     * @return Agent 执行结果
     */
    public AgentResult run(Agent agent, AgentTask task, String sessionId, String... extraContext) {
        String host = runtimeConfig.get(ConfigKeys.LLM_HOST);
        String apiKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY);
        String model = runtimeConfig.get(ConfigKeys.LLM_MODEL, "gpt-4o-mini");

        if (host == null || host.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("[AgentEngine] LLM 未配置，降级到 process()");
            return agent.process(task);
        }

        // 1. 初始化或恢复会话上下文
        final boolean isNewSession = sessionId == null || sessionId.isBlank();
        final String sid = isNewSession ? "static-" + System.nanoTime() : sessionId;
        List<Map<String, Object>> messages;

        if (isNewSession) {
            messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", agent.getSystemPrompt(task)));
            for (String ctx : extraContext) {
                if (ctx != null && !ctx.isBlank()) {
                    messages.add(Map.of("role", "system", "content", ctx));
                }
            }
            messages.add(Map.of("role", "user", "content", task.getPayload().toString()));
        } else {
            // 复用已有会话上下文
            messages = SessionContext.getMessages(sid);
            if (messages.isEmpty()) {
                messages.add(Map.of("role", "system", "content", agent.getSystemPrompt(task)));
                for (String ctx : extraContext) {
                    if (ctx != null && !ctx.isBlank()) {
                        messages.add(Map.of("role", "system", "content", ctx));
                    }
                }
            }
            messages.add(Map.of("role", "user", "content", task.getPayload().toString()));
        }

        // 2. 构建 tools
        List<Map<String, Object>> tools = agent.getTools().stream()
                .map(ToolSchema::toOpenAISpec)
                .toList();

        String url = (host.endsWith("/") ? host : host + "/") + "chat/completions";
        RestClient client = RestClient.builder().build();

        // 3. 推理循环
        for (int round = 0; round < MAX_ROUNDS; round++) {
            log.debug("[AgentEngine] round={}, agent={}, msgCount={}, tools={}",
                    round, agent.agentId(), messages.size(), tools.size());

            // 3a. 调 LLM — 校验消息合法性后发送
            Map<String, Object> request = buildRequest(model, messages, tools);

            // 诊断：统计消息角色分布
            long sysCount = messages.stream().filter(m -> "system".equals(m.get("role"))).count();
            long userCount = messages.stream().filter(m -> "user".equals(m.get("role"))).count();
            long asstCount = messages.stream().filter(m -> "assistant".equals(m.get("role"))).count();
            long toolCount = messages.stream().filter(m -> "tool".equals(m.get("role"))).count();
            int lastIdx = messages.size() - 1;
            String last3roles = (lastIdx >= 0 ? messages.get(lastIdx).get("role") : "-") + "|"
                    + (lastIdx >= 1 ? messages.get(lastIdx - 1).get("role") : "-") + "|"
                    + (lastIdx >= 2 ? messages.get(lastIdx - 2).get("role") : "-");
            log.info("[AgentEngine] LLM req: agent={}, round={}, msgs={}(sys={},usr={},asst={},tool={}), last3={}",
                    agent.agentId(), round, messages.size(), sysCount, userCount, asstCount, toolCount, last3roles);

            Map<String, Object> response;
            try {
                response = client.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey)
                        .body(request)
                        .retrieve()
                        .body(Map.class);
            } catch (Exception e) {
                log.error("[AgentEngine] LLM 调用失败 round={}: agent={}, msgCnt={}, err={}",
                        round, agent.agentId(), messages.size(), e.getMessage());
                if (!isNewSession) SessionContext.reset(sid);
                return AgentResult.builder()
                        .success(false).errorMessage("LLM 调用失败: " + e.getMessage()).build();
            }

            // 3b. 提取 assistant 消息
            Map<String, Object> assistantMsg = extractAssistantMessage(response);
            if (assistantMsg == null) {
                if (!isNewSession) SessionContext.reset(sid);
                return AgentResult.builder()
                        .success(false).errorMessage("LLM 返回空结果").build();
            }
            messages.add(assistantMsg);
            if (!isNewSession) SessionContext.addMessage(sid, assistantMsg);

            // 3c. 检查是否有 tool_calls
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistantMsg.get("tool_calls");

            if (toolCalls == null || toolCalls.isEmpty()) {
                // 最终答案
                String content = (String) assistantMsg.get("content");
                log.info("[AgentEngine] 完成: agent={}, rounds={}, len={}",
                        agent.agentId(), round + 1, content != null ? content.length() : 0);
                if (!isNewSession) SessionContext.reset(sid);
                return AgentResult.builder()
                        .success(true)
                        .data(Map.of("response", content != null ? content : ""))
                        .build();
            }

            // 3d. 执行工具 → 截断结果后喂回（长短上下文分离）
            for (Map<String, Object> tc : toolCalls) {
                String toolCallId = (String) tc.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                String toolName = (String) function.get("name");
                String arguments = (String) function.get("arguments");

                log.info("[AgentEngine] 执行工具: agent={}, tool={}, toolCallId={}, argsType={}, args={}",
                        agent.agentId(), toolName, toolCallId, arguments != null ? arguments.getClass().getSimpleName() : "null",
                        arguments != null ? (arguments.length() > 120 ? arguments.substring(0, 120) + "..." : arguments) : "null");

                ToolResult result = toolRegistry.execute(agent.agentId(), toolName, arguments);

                // ════════════════════════════════════════════
                // 长短上下文分离：只返回简短摘要，不喂回长文本
                // ════════════════════════════════════════════
                String shortResult = summarizeToolResult(result, toolName);

                Map<String, Object> toolMsg = Map.of(
                        "role", "tool",
                        "tool_call_id", toolCallId,
                        "content", shortResult
                );
                messages.add(toolMsg);
                if (!isNewSession) SessionContext.addMessage(sid, toolMsg);

                // 诊断：工具执行后打印消息列表快照
                log.info("[AgentEngine] post-tool: agent={}, tool={}, msgCnt={}, roles={}",
                        agent.agentId(), toolName, messages.size(),
                        messages.stream().map(m -> (String)m.get("role")).toList());
            }
        }

        log.warn("[AgentEngine] 达到最大推理轮次({}): agent={}", MAX_ROUNDS, agent.agentId());
        if (!isNewSession) SessionContext.reset(sid);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("response", "已达到最大推理轮次，建议简化问题后重试"))
                .build();
    }

    // ================================================================
    //  [重构] 工具结果序列化 — 使用 Jackson 确保合法 JSON
    //  不再使用 Map.toString()（输出 {key=value, ...} 非 JSON 格式）
    //  不再手动拼接 JSON（存在转义遗漏风险）
    // ================================================================
    private String summarizeToolResult(ToolResult result, String toolName) {
        if (result == null) {
            return "{\"status\":\"error\",\"message\":\"工具返回空\"}";
        }
        if (!result.success()) {
            // 使用 LinkedHashMap 避免 Map.of 对 null errorMessage 抛 NPE
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("status", "error");
            err.put("tool", toolName);
            String msg = result.errorMessage();
            if (msg != null) err.put("message", msg);
            return toJson(err);
        }
        Object data = result.data();
        if (data == null) {
            return toJson(Map.of("status", "success", "tool", toolName));
        }

        // 如果 data 已经是合法 JSON 字符串且长度在限制内，直接返回
        if (data instanceof String str) {
            if (str.length() <= MAX_TOOL_RESULT_LENGTH && str.matches("^\\s*[\\[{].*[\\]}]\\s*$")) {
                return str;
            }
            // 截断长字符串
            if (str.length() > MAX_TOOL_RESULT_LENGTH) {
                return toJson(Map.of(
                    "status", "success",
                    "tool", toolName,
                    "summary", str.substring(0, MAX_TOOL_RESULT_LENGTH) + "...",
                    "_truncated", true
                ));
            }
            return str;
        }

        // data 是 Map → 用 Jackson 序列化, 保证合法 JSON
        if (data instanceof Map<?, ?> map) {
            // 检查是否有长文本字段, 截断之
            Map<String, Object> cleaned = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object val = e.getValue();
                if (val instanceof String s && s.length() > 200
                        && List.of("content", "description", "lesson", "answer", "text", "body").contains(key)) {
                    cleaned.put(key, s.substring(0, 80) + "...");
                    cleaned.put(key + "_truncated", true);
                } else {
                    cleaned.put(key, val);
                }
                // 防止 Map 键值过多撑爆上下文
                if (cleaned.size() >= 10) {
                    cleaned.put("_field_count", cleaned.size() + "+");
                    break;
                }
            }
            String json = toJson(cleaned);
            // 如果序列化后还是超长, 做最终截断（使用 LinkedHashMap 避免 Map.of 对 null 值抛 NPE）
            if (json.length() > MAX_TOOL_RESULT_LENGTH) {
                Map<String, Object> trimmed = new java.util.LinkedHashMap<>();
                trimmed.put("status", "success");
                trimmed.put("tool", toolName);
                trimmed.put("_truncated", true);
                // 优先取 resourceId，失败取 pathId，再不济留空
                Object rid = map.get("resourceId");
                if (rid == null) rid = map.get("pathId");
                if (rid != null) trimmed.put("resourceId", rid);
                // 加一条摘要字段帮助 LLM 理解任务已执行
                Object goal = map.get("goal");
                if (goal != null) trimmed.put("goal", goal);
                return toJson(trimmed);
            }
            return json;
        }

        // 其他类型 → 截断到限制长度
        String raw = data.toString();
        if (raw.length() > MAX_TOOL_RESULT_LENGTH) {
            raw = raw.substring(0, MAX_TOOL_RESULT_LENGTH) + "...";
        }
        return raw;
    }

    /** Jackson 序列化，确保输出合法 JSON */
    private String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[AgentEngine] JSON 序列化失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"序列化失败\"}";
        }
    }

    /** 构建 OpenAI API 请求体 */
    private Map<String, Object> buildRequest(String model,
                                              List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    /** 从 API 响应中提取 assistant 消息 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAssistantMessage(Map<String, Object> response) {
        if (response == null) return null;
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return null;
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return message;
    }
}
