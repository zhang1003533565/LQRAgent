package com.lqragent.backend.agents.diagram.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.agents.mediageneration.service.MediaGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateDiagramTool implements AgentTool {
    
    private final AiServerWsProxy aiServerWsProxy;
    private final MediaGenerationService mediaGenerationService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_diagram"; }
    
    @Override
    public String description() { return "调用 ai-server 生成图表或示意图"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "图表主题"),
                        "type", Map.of("type", "string", "description", "图表类型: mermaid/image"),
                        "kpId", Map.of("type", "string", "description", "知识点ID（可选）")
                ),
                "required", new String[]{"topic"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String topic = args.get("topic") != null ? args.get("topic").toString() : "学习路径";
            String type = args.get("type") != null ? args.get("type").toString() : "mermaid";
            String kpId = args.get("kpId") != null ? args.get("kpId").toString() : null;
            
            // 调用 ai-server 生成 Mermaid
            String mermaidCode = aiServerWsProxy.generateMermaid(topic + " 学习路线图");
            
            if (mermaidCode != null && !mermaidCode.isBlank()) {
                Map<String, Object> data = Map.of(
                        "topic", topic,
                        "type", "mermaid",
                        "diagram", mermaidCode,
                        "source", "ai-server",
                        "summary", "已生成「" + topic + "」的学习路线图"
                );
                return ToolResult.success(mapper.writeValueAsString(data));
            }
            
            // 降级：生成基本 Mermaid
            String fallbackMermaid = "graph TD\n" +
                "    A[" + topic + "] --> B[基础知识]\n" +
                "    A --> C[核心概念]\n" +
                "    A --> D[实践应用]\n" +
                "    B --> E[理论学习]\n" +
                "    C --> F[深入理解]\n" +
                "    D --> G[项目实战]\n" +
                "    E --> H[掌握]\n" +
                "    F --> H\n" +
                "    G --> H";
            
            Map<String, Object> data = Map.of(
                    "topic", topic,
                    "type", "mermaid",
                    "diagram", fallbackMermaid,
                    "source", "fallback",
                    "summary", "已生成「" + topic + "」的学习路线图"
            );
            return ToolResult.success(mapper.writeValueAsString(data));
        } catch (Exception e) {
            return ToolResult.failure("生成图表失败: " + e.getMessage());
        }
    }
}
