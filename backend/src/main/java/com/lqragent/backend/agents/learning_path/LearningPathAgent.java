package com.lqragent.backend.agents.learning_path;

import com.lqragent.backend.framework.Agent;
import com.lqragent.backend.framework.AgentIds;
import com.lqragent.backend.framework.AgentResult;
import com.lqragent.backend.framework.AgentTask;
import com.lqragent.backend.framework.RequestContext;
import com.lqragent.backend.framework.ToolRegistry;
import com.lqragent.backend.framework.ToolSchema;
import com.lqragent.backend.agents.learning_path.service.LearningPathService;
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
        String goal = (String) task.getPayload().getOrDefault("goal", "");
        var dto = learningPathService.generatePath(userId, goal, null);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("pathId", dto.getPathId(), "goal", dto.getGoal()))
                .build();
    }

    @Override
    public String getSystemPrompt(AgentTask task) {
        return "你是学习路径规划专家。先用 BFS 找到最短路径，再结合学生画像做个性化排序。";
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
