package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.agents.learn.path.service.LearningPathService;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 路径规划智能体（Orchestrator 版本）
 * LLM 推理 + 真实 Service 调用
 */
@Slf4j
@Component("orchLearningPathAgent")
public class OrchLearningPathAgent extends OrchBaseAgent {

    private final LearningPathService pathService;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrchLearningPathAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                                 LearningPathService pathService) {
        super(AgentIds.LEARNING_PATH, streams, applicationContext);
        this.pathService = pathService;
    }
    
    @Override
    protected String getAgentBeanName() {
        return "learningPathAgent";
    }
    
    @Override
    protected Map<String, Object> callService(AgentMessage request, String action) {
        if ("generate_path".equals(action)) {
            try {
                String goal = (String) request.getContent().getOrDefault("goal", "");
                String userIdStr = request.getContent().get("userId") != null
                        ? request.getContent().get("userId").toString() : "2";
                Long userId = Long.parseLong(userIdStr);
                
                var path = pathService.generatePath(userId, goal, null);
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("path", path);
                return result;
            } catch (Exception e) {
                log.error("[{}] callService failed: {}", agentId, e.getMessage(), e);
                return null;
            }
        }
        return null;
    }
}
