package com.lqragent.backend.agents.resourcegeneration.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateResponse;
import com.lqragent.backend.agents.resourcegeneration.service.ResourceGenerationService;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateResourceTool implements AgentTool {
    
    private final ResourceGenerationService resourceService;
    private final KnowledgeGraphService kgService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_resource"; }
    
    @Override
    public String description() { return "生成学习资源：讲义、练习题、代码示例等。topic可以是知识点ID(kpId)或知识点名称。"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "资源主题/知识点ID或名称"),
                        "resourceType", Map.of("type", "string", "description", "资源类型：LESSON/QUIZ/CODE_CASE/MIND_MAP/EXTENDED_READING"),
                        "pathData", Map.of("type", "string", "description", "学习路径数据（JSON）")
                ),
                "required", new String[]{"topic"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String topic = args.get("topic") != null ? args.get("topic").toString().trim() : "Unknown";
            String resourceType = args.get("resourceType") != null ? args.get("resourceType").toString() : "LESSON";
            
            // 尝试将 topic 解析为有效的 kpId
            String resolvedKpId = resolveTopicToKpId(topic);
            
            if (resolvedKpId == null) {
                // 无法匹配知识点，直接返回提示
                log.warn("[GenerateResourceTool] 无法匹配知识点: topic={}", topic);
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "topic", topic,
                        "resourceType", resourceType,
                        "message", "未找到匹配的知识点，请提供更具体的知识点ID或在知识图谱中存在的主题名称",
                        "content", "## " + topic + "\n\n抱歉，系统中未找到与「" + topic + "」匹配的知识点。\n\n请尝试：\n1. 使用知识图谱中存在的知识点名称\n2. 先通过 QA 功能查询相关知识\n3. 在知识图谱管理中添加该知识点后再生成资源"
                )));
            }
            
            // 通过 ResourceGenerationService.generate() 生成
            ResourceGenerateRequest request = ResourceGenerateRequest.builder()
                    .kpId(resolvedKpId)
                    .resourceType(resourceType)
                    .customPrompt(topic)
                    .build();
            
            ResourceGenerateResponse response = resourceService.generate(request);
            
            Map<String, Object> data = Map.of(
                    "topic", topic,
                    "resourceType", resourceType,
                    "resourceId", response.getResourceId() != null ? response.getResourceId().toString() : "",
                    "title", response.getTitle() != null ? response.getTitle() : "",
                    "content", response.getContent() != null ? response.getContent() : "",
                    "format", "markdown"
            );
            return ToolResult.success(mapper.writeValueAsString(data));
        } catch (Exception e) {
            log.error("[GenerateResourceTool] 生成资源失败: {}", e.getMessage());
            return ToolResult.failure("生成资源失败: " + e.getMessage());
        }
    }
    
    /**
     * 尝试将 topic 文本解析为有效的 kpId
     * 1. 直接匹配 kpId
     * 2. 模糊匹配知识点的 title
     * 3. 模糊匹配知识点的 description
     */
    private String resolveTopicToKpId(String topic) {
        try {
            // 尝试直接作为 kpId 查找
            var direct = kgService.getByKpId(topic);
            if (direct.isPresent()) {
                log.info("[GenerateResourceTool] 直接匹配 kpId: {}", topic);
                return topic;
            }
            
            // 模糊匹配：在所有知识点中查找
            var allKps = kgService.getAll();
            if (allKps == null || allKps.isEmpty()) {
                return null;
            }
            
            // 精确匹配 title
            for (KnowledgePoint kp : allKps) {
                if (kp.getTitle() != null && kp.getTitle().equals(topic)) {
                    log.info("[GenerateResourceTool] 精确匹配 title: {} -> {}", topic, kp.getKpId());
                    return kp.getKpId();
                }
            }
            
            // 包含匹配 title
            for (KnowledgePoint kp : allKps) {
                if (kp.getTitle() != null && kp.getTitle().contains(topic)) {
                    log.info("[GenerateResourceTool] 包含匹配 title: {} -> {}", topic, kp.getKpId());
                    return kp.getKpId();
                }
            }
            
            // topic 包含 title
            for (KnowledgePoint kp : allKps) {
                if (kp.getTitle() != null && topic.contains(kp.getTitle())) {
                    log.info("[GenerateResourceTool] topic包含title: {} -> {}", topic, kp.getKpId());
                    return kp.getKpId();
                }
            }
            
            // 匹配 description
            for (KnowledgePoint kp : allKps) {
                if (kp.getDescription() != null && kp.getDescription().contains(topic)) {
                    log.info("[GenerateResourceTool] description匹配: {} -> {}", topic, kp.getKpId());
                    return kp.getKpId();
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("[GenerateResourceTool] kpId解析异常: {}", e.getMessage());
            return null;
        }
    }
}
