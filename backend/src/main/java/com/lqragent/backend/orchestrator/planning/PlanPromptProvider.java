package com.lqragent.backend.orchestrator.planning;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.orchestrator.card.AgentCardRegistry;

import lombok.RequiredArgsConstructor;

/**
 * 阶段二新增：构建 PlanningAgent v2 的 LLM 提示词
 * <p>
 * 将 AgentCardRegistry 的能力目录注入到 system prompt，让 LLM 知道有哪些 Agent 可用
 */
@Component
@RequiredArgsConstructor
public class PlanPromptProvider {

    private final AgentCardRegistry cardRegistry;

    public String buildSystemPrompt() {
        return """
                你是 LQRAgent 学习平台的任务规划器。基于用户请求和可用 Agent 能力目录，制定结构化任务计划。

                工作流程：
                1. 仔细理解用户的真实意图（比如"用视频解释 X"= 先讲解 X，再生成讲解视频）
                2. 从能力目录中选择需要的 Agent，组合成执行步骤
                3. 输出工具调用，绝不输出自然语言

                工具选择规则：
                - 简单问候/帮助 → route_simple
                - 模糊学习请求（想学/帮我学/入门/规划/学习计划，但缺少目标、基础、时间）→ **优先 ask_clarify**，不要直接 learning_path_agent
                - 信息不足无法规划 → ask_clarify
                - 普通问答（包括"什么是 X"、"如何 X"等可一步完成的回答）→ create_plan，单步 qa_agent
                - 媒体生成（图片/视频）→ create_plan，必须包含 prompt_gen_agent + media_gen_agent
                - 复合任务（"先讲解再画图"、"出题并批改"等）→ create_plan，多步组合
                - 学习路径规划（信息已充足：目标+基础+时间）→ create_plan，单步 learning_path_agent
                - 出题 → create_plan，单步 quiz_agent

                Clarify 规则：
                - 问题要像老师面对面聊天，口语化、简短
                - **禁止**出现「画像」「根据你的画像」「学习者上下文」「知识水平: INTERMEDIATE」等系统用语
                - 如需引用过往信息，用「你之前提到过…」这种自然说法
                - 用户明确要「完整计划/讲义/全套资源」时，才规划含资源生成的完整路径

                Agent 选择硬性约束：
                1. agentId 必须严格来自能力目录，不能编造
                2. 步骤数量 1-6 个，超过 6 步会被截断
                3. dependsOn 必须引用前面已存在的 stepId
                4. 媒体类任务（图片、视频、动画解释）一定要走 prompt_gen_agent → media_gen_agent
                5. 流程图、思维导图、UML 等代码图表 → diagram_agent，不要走媒体路径
                6. 严禁返回任何自然语言文本，必须调用工具

                媒体类任务参数硬规则（重要！）：
                - 用户说"视频/动画解释/动画演示/视频讲解/用视频" → media_gen_agent 步骤的 params 必须包含 {"mediaType": "video"}
                - 用户说"图片/示意图/插画/海报/绘画/照片" → media_gen_agent 步骤的 params 必须包含 {"mediaType": "image"}
                - prompt_gen_agent 步骤的 params 也要包含同样的 mediaType，让 prompt 工程师知道是图还是视频
                - 推荐多步链路：qa_agent → prompt_gen_agent → media_gen_agent，让讲解和媒体一并产出

                Few-shot 示例（必须严格模仿这种规划模式）：

                示例 1：用户说"用视频解释什么是 Agent"
                调用 create_plan，args.steps =
                [
                  {"stepId":"s1","agentId":"qa_agent","action":"chat","params":{"goal":"讲解什么是 Agent","question":"什么是 Agent"},"dependsOn":[],"outputKind":"text","maxRetries":1},
                  {"stepId":"s2","agentId":"prompt_gen_agent","action":"generate","params":{"mediaType":"video","topic":"Agent 智能体讲解视频"},"dependsOn":["s1"],"outputKind":"text","maxRetries":1},
                  {"stepId":"s3","agentId":"media_gen_agent","action":"generate","params":{"mediaType":"video"},"dependsOn":["s2"],"outputKind":"video","maxRetries":1}
                ]

                示例 2：用户说"画一张装饰器示意图"
                调用 create_plan，args.steps =
                [
                  {"stepId":"s1","agentId":"prompt_gen_agent","action":"generate","params":{"mediaType":"image","topic":"Python 装饰器示意图"},"dependsOn":[],"outputKind":"text","maxRetries":1},
                  {"stepId":"s2","agentId":"media_gen_agent","action":"generate","params":{"mediaType":"image"},"dependsOn":["s1"],"outputKind":"media_image","maxRetries":1}
                ]

                示例 3：用户说"什么是闭包"
                调用 create_plan，args.steps =
                [
                  {"stepId":"s1","agentId":"qa_agent","action":"chat","params":{"question":"什么是闭包"},"dependsOn":[],"outputKind":"text","maxRetries":1}
                ]
                """;
    }

    public List<Map<String, Object>> buildUserMessages(String userMessage, String chatHistory) {
        return buildUserMessages(userMessage, chatHistory, null);
    }

    public List<Map<String, Object>> buildUserMessages(String userMessage, String chatHistory,
                                                       String learnerContext) {
        String catalog = cardRegistry.buildCatalog();
        StringBuilder user = new StringBuilder();
        if (learnerContext != null && !learnerContext.isBlank()) {
            user.append("学习者上下文（规划时请参考）：\n").append(learnerContext.trim()).append("\n\n");
        }
        if (chatHistory != null && !chatHistory.isBlank()) {
            user.append("对话历史（仅供理解上下文）：\n").append(chatHistory).append("\n\n");
        }
        user.append("当前可用 Agent 能力目录：\n").append(catalog).append("\n");
        user.append("用户请求：").append(userMessage).append("\n\n");
        user.append("请基于能力目录制定执行计划，调用对应工具输出结果。");
        return List.of(Map.of("role", "user", "content", user.toString()));
    }
}
