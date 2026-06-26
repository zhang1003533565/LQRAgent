package com.lqragent.backend.agents.path;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.path.tools.GeneratePathTool;
import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.orchestrator.consultation.ConsultationEngine;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class LearningPathAgent extends BaseAgent {

    private final GeneratePathTool generatePathTool;
    private final ConsultationEngine consultationEngine;

    public LearningPathAgent(RedisStreamsService streams, LlmClient llmClient,
                              AgentToolRegistry toolRegistry, GeneratePathTool generatePathTool,
                              ConsultationEngine consultationEngine,
                              PromptService promptService) {
        super(AgentIds.LEARNING_PATH, streams, llmClient, toolRegistry, promptService);
        this.generatePathTool = generatePathTool;
        this.consultationEngine = consultationEngine;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(generatePathTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    public AgentResponse process(AgentRequest request, TaskContext context) {
        if ("consult_path".equals(request.action())) {
            return consultationEngine.consultAsAgentResponse(request, context);
        }
        return super.process(request, context);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String action = (String) request.getContent().getOrDefault("action", "generate_path");

        // 阶段五新增：根据薄弱点调整路径
        if ("adjust_path".equals(action)) {
            Object weakness = request.getContent().getOrDefault("weakness_profile",
                    request.getContent().getOrDefault("weakness", ""));
            String userId = (String) request.getContent().getOrDefault("userId", "0");
            StringBuilder sb = new StringBuilder();
            sb.append("用户 ").append(userId).append(" 的薄弱点分析：\n");
            sb.append(weakness).append("\n\n");
            sb.append("请根据薄弱点调整学习路径：\n");
            sb.append("1. 在路径中增加薄弱知识点的强化节点\n");
            sb.append("2. 调整薄弱点相关节点的难度和顺序\n");
            sb.append("3. 保留已掌握的节点（标记为已完成）\n");
            return sb.toString();
        }

        // 兼容旧调用
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        String goal = (String) request.getContent().getOrDefault("goal", "");
        StringBuilder sb = new StringBuilder();
        sb.append("请为用户 ").append(userId).append(" 生成学习路径，目标：").append(goal).append("\n\n");
        return sb.toString();
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.LEARNING_PATH,
                "学习路径规划",
                "生成或调整个性化学习路径",
                List.of("learning_path", "path", "plan", "roadmap", "study_plan"),
                List.of(ToolSpec.of("generate_path", "生成学习路径")),
                List.of("text", "weakness_profile"),
                List.of("learning_path"),
                1, 60000L
        );
    }
}
