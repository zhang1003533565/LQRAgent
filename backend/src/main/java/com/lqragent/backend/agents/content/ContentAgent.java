package com.lqragent.backend.agents.content;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.content.tools.AnalyzeContentTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContentAgent extends BaseAgent {
    
    private final AnalyzeContentTool analyzeContentTool;
    
    public ContentAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                        AnalyzeContentTool analyzeContentTool) {
        super(AgentIds.CONTENT_ANALYSIS, llmClient, toolRegistry);
        this.analyzeContentTool = analyzeContentTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/content/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(analyzeContentTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String kbName = request.context() != null
                ? request.context().getOrDefault("kbName", "").toString()
                : "";
        String fileName = request.context() != null
                ? request.context().getOrDefault("fileName", "").toString()
                : "";
        return String.format("请分析知识库「%s」中的文件「%s」，提取关键信息和知识点。", kbName, fileName);
    }
}
