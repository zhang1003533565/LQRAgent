package com.lqragent.backend.agents.learningpath;

import com.lqragent.backend.core.agent.Agent;
import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import com.lqragent.backend.core.agent.AgentTask;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.core.tool.ToolRegistry;
import com.lqragent.backend.core.tool.ToolSchema;
import com.lqragent.backend.agents.learningpath.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LearningPathAgent implements Agent {

    private final LearningPathService learningPathService;

    @Override
    public String agentId() { return AgentIds.LEARNING_PATH; }

    @Override
    public AgentResult process(AgentTask task) {
        Long userId = task.getUserId();
        String goal = (String) task.getPayload().getOrDefault("goal",
                task.getPayload().getOrDefault("message", ""));
        var dto = learningPathService.generatePath(userId, goal, null);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("pathId", dto.getPathId(), "goal", dto.getGoal()))
                .build();
    }

    @Override
    public String getSystemPrompt(AgentTask task) {
        return """
            你是学习路径规划专家。你的任务是为学生生成个性化的学习路径。

            ## 输出规范
            - 调用 generate_path 工具获取路径数据
            - 收到工具结果后，用自然语言整理成清晰的学习路径
            - 按阶段分组（基础→进阶→高级→实战），每个知识点用序号标注
            - 不要输出"让我按照...的思路来给你完整展示"之类的元评论
            - 不要输出"看起来系统返回的数据比较简略"之类的系统评论
            - 直接输出干净的学习路径内容，开头用一两句话引出即可
            - 如果工具返回了 follow_up 问卷，将其整合到路径末尾
            """;
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("generate_path", "生成学习路径（BFS + 可选 LLM 排序）",
                ToolSchema.params(Map.of(
                    "goal", ToolSchema.stringParam("学习目标", "如：Python 装饰器")
                ), "goal"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "generate_path", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            // userId 从 RequestContext 获取，LLM 不需要传入
            Long userId = RequestContext.getUserId();
            if (userId == null) {
                return Map.of("status", "error", "message", "无法获取用户身份");
            }
            String goal = (String) p.getOrDefault("goal", "");
            var dto = learningPathService.generatePath(userId, goal, null);
            return Map.of("pathId", dto.getPathId(), "goal", dto.getGoal(), "nodes", dto.getNodes());
        });
    }
}
