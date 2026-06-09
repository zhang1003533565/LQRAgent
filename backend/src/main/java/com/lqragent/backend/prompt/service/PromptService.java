package com.lqragent.backend.prompt.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lqragent.backend.prompt.entity.AgentPrompt;
import com.lqragent.backend.prompt.repository.AgentPromptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 提示词管理服务
 * 
 * 功能：
 * 1. 从数据库加载提示词（优先级最高）
 * 2. 从文件加载提示词（兜底）
 * 3. 支持运行时修改提示词
 * 4. 支持重置为默认值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final AgentPromptRepository promptRepository;

    /** 内存缓存：agentId -> promptContent */
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    /** Agent 提示词文件路径映射 */
    private static final Map<String, String> AGENT_PROMPT_PATHS = Map.ofEntries(
        Map.entry("qa_agent", "agents/qa/prompts/system.md"),
        Map.entry("profile_agent", "agents/profile/prompts/system.md"),
        Map.entry("effect_agent", "agents/effect/prompts/system.md"),
        Map.entry("learning_path_agent", "agents/learningpath/prompts/system.md"),
        Map.entry("quality_agent", "agents/quality/prompts/system.md"),
        Map.entry("resource_agent", "agents/resource/prompts/system.md"),
        Map.entry("content_analysis_agent", "agents/content/prompts/system.md"),
        Map.entry("planning_agent", "agents/planning/prompts/system.md"),
        // 学习科学层
        Map.entry("knowledge_state_agent", "agents/knowledgestate/prompts/system.md"),
        Map.entry("spaced_repetition_agent", "agents/spacedrepetition/prompts/system.md"),
        Map.entry("difficulty_agent", "agents/difficulty/prompts/system.md"),
        Map.entry("learning_style_agent", "agents/learningstyle/prompts/system.md"),
        // 内容生成层
        Map.entry("diagram_agent", "agents/diagram/prompts/system.md"),
        Map.entry("summary_agent", "agents/summary/prompts/system.md"),
        // 智能服务层
        Map.entry("recommendation_agent", "agents/recommendation/prompts/system.md"),
        Map.entry("assessment_agent", "agents/assessment/prompts/system.md"),
        Map.entry("intervention_agent", "agents/intervention/prompts/system.md"),
        // 媒体生成
        Map.entry("prompt_gen_agent", "agents/promptgen/prompts/system.md"),
        Map.entry("media_gen_agent", "agents/mediagen/prompts/system.md")
    );

    /** Agent 显示名称映射 */
    private static final Map<String, String> AGENT_NAMES = Map.ofEntries(
        Map.entry("qa_agent", "答疑智能体"),
        Map.entry("profile_agent", "画像智能体"),
        Map.entry("effect_agent", "效果评估智能体"),
        Map.entry("learning_path_agent", "学习路径智能体"),
        Map.entry("quality_agent", "质量检查智能体"),
        Map.entry("resource_agent", "资源生成智能体"),
        Map.entry("content_analysis_agent", "内容分析智能体"),
        Map.entry("planning_agent", "任务规划智能体"),
        // 学习科学层
        Map.entry("knowledge_state_agent", "知识状态智能体"),
        Map.entry("spaced_repetition_agent", "间隔重复智能体"),
        Map.entry("difficulty_agent", "难度评估智能体"),
        Map.entry("learning_style_agent", "学习风格智能体"),
        // 内容生成层
        Map.entry("diagram_agent", "图表生成智能体"),
        Map.entry("summary_agent", "摘要生成智能体"),
        // 智能服务层
        Map.entry("recommendation_agent", "推荐智能体"),
        Map.entry("assessment_agent", "测评智能体"),
        Map.entry("intervention_agent", "干预智能体"),
        // 媒体生成
        Map.entry("prompt_gen_agent", "Prompt生成智能体"),
        Map.entry("media_gen_agent", "媒体生成智能体")
    );

    /**
     * 获取 Agent 的提示词
     * 优先级：数据库 > 内存缓存 > 文件
     */
    public String getPrompt(String agentId) {
        // 1. 检查内存缓存
        String cached = promptCache.get(agentId);
        if (cached != null) {
            return cached;
        }

        // 2. 从数据库加载
        Optional<AgentPrompt> dbPrompt = promptRepository.findByAgentId(agentId);
        if (dbPrompt.isPresent()) {
            String content = dbPrompt.get().getPromptContent();
            promptCache.put(agentId, content);
            return content;
        }

        // 3. 从文件加载
        String filePath = AGENT_PROMPT_PATHS.get(agentId);
        if (filePath != null) {
            String fileContent = loadPromptFromFile(filePath);
            if (fileContent != null) {
                // 保存到数据库
                saveDefaultPrompt(agentId, fileContent);
                promptCache.put(agentId, fileContent);
                return fileContent;
            }
        }

        log.warn("[PromptService] no prompt found for agent: {}", agentId);
        return "You are a helpful assistant.";
    }

    /**
     * 更新提示词
     */
    @Transactional
    public AgentPrompt updatePrompt(String agentId, String content, String updatedBy) {
        AgentPrompt prompt = promptRepository.findByAgentId(agentId)
            .orElseGet(() -> {
                String defaultContent = loadDefaultPrompt(agentId);
                return AgentPrompt.builder()
                    .agentId(agentId)
                    .agentName(AGENT_NAMES.getOrDefault(agentId, agentId))
                    .defaultContent(defaultContent)
                    .build();
            });

        prompt.setPromptContent(content);
        prompt.setVersion(prompt.getVersion() + 1);
        prompt.setUpdatedBy(updatedBy);

        AgentPrompt saved = promptRepository.save(prompt);
        promptCache.put(agentId, content);

        log.info("[PromptService] updated prompt for agent: {}, version: {}", agentId, saved.getVersion());
        return saved;
    }

    /**
     * 重置为默认值
     */
    @Transactional
    public AgentPrompt resetPrompt(String agentId) {
        Optional<AgentPrompt> optional = promptRepository.findByAgentId(agentId);
        if (optional.isEmpty()) {
            throw new IllegalArgumentException("Agent prompt not found: " + agentId);
        }

        AgentPrompt prompt = optional.get();
        if (prompt.getDefaultContent() == null) {
            throw new IllegalStateException("No default content available for: " + agentId);
        }

        prompt.setPromptContent(prompt.getDefaultContent());
        prompt.setVersion(prompt.getVersion() + 1);
        prompt.setUpdatedBy("system");

        AgentPrompt saved = promptRepository.save(prompt);
        promptCache.put(agentId, saved.getPromptContent());

        log.info("[PromptService] reset prompt for agent: {}", agentId);
        return saved;
    }

    /**
     * 获取所有提示词
     */
    @Transactional
    public List<AgentPrompt> getAllPrompts() {
        List<AgentPrompt> prompts = promptRepository.findAll();
        
        // 确保所有已知 Agent 都有记录
        for (Map.Entry<String, String> entry : AGENT_PROMPT_PATHS.entrySet()) {
            String agentId = entry.getKey();
            boolean exists = prompts.stream()
                .anyMatch(p -> p.getAgentId().equals(agentId));
            
            if (!exists) {
                String defaultContent = loadDefaultPrompt(agentId);
                if (defaultContent != null) {
                    try {
                        AgentPrompt newPrompt = AgentPrompt.builder()
                            .agentId(agentId)
                            .agentName(AGENT_NAMES.getOrDefault(agentId, agentId))
                            .promptContent(defaultContent)
                            .defaultContent(defaultContent)
                            .build();
                        AgentPrompt saved = promptRepository.save(newPrompt);
                        prompts.add(saved);
                        promptCache.put(agentId, defaultContent);
                    } catch (Exception e) {
                        // 并发创建冲突，从数据库重新查询
                        log.warn("[PromptService] concurrent create conflict in getAllPrompts for: {}", agentId);
                        promptRepository.findByAgentId(agentId).ifPresent(prompts::add);
                    }
                }
            }
        }
        
        return prompts;
    }

    /**
     * 获取指定 Agent 的提示词详情
     * 如果数据库中没有记录，自动从文件加载并创建
     */
    @Transactional
    public Optional<AgentPrompt> getPromptDetail(String agentId) {
        // 1. 尝试从数据库获取
        Optional<AgentPrompt> dbPrompt = promptRepository.findByAgentId(agentId);
        if (dbPrompt.isPresent()) {
            return dbPrompt;
        }
        
        // 2. 如果数据库没有，尝试从文件加载并创建
        String defaultContent = loadDefaultPrompt(agentId);
        if (defaultContent != null) {
            try {
                AgentPrompt newPrompt = AgentPrompt.builder()
                    .agentId(agentId)
                    .agentName(AGENT_NAMES.getOrDefault(agentId, agentId))
                    .promptContent(defaultContent)
                    .defaultContent(defaultContent)
                    .build();
                AgentPrompt saved = promptRepository.save(newPrompt);
                promptCache.put(agentId, defaultContent);
                log.info("[PromptService] created prompt from file for agent: {}", agentId);
                return Optional.of(saved);
            } catch (Exception e) {
                // 并发创建导致的唯一约束冲突，重试查询
                log.warn("[PromptService] concurrent create conflict for agent: {}, retrying query", agentId);
                return promptRepository.findByAgentId(agentId);
            }
        }
        
        return Optional.empty();
    }

    /**
     * 保存默认提示词
     */
    private void saveDefaultPrompt(String agentId, String content) {
        if (!promptRepository.existsByAgentId(agentId)) {
            AgentPrompt prompt = AgentPrompt.builder()
                .agentId(agentId)
                .agentName(AGENT_NAMES.getOrDefault(agentId, agentId))
                .promptContent(content)
                .defaultContent(content)
                .build();
            promptRepository.save(prompt);
        }
    }

    /**
     * 加载默认提示词
     */
    private String loadDefaultPrompt(String agentId) {
        String filePath = AGENT_PROMPT_PATHS.get(agentId);
        if (filePath != null) {
            return loadPromptFromFile(filePath);
        }
        return null;
    }

    /**
     * 从文件加载提示词
     */
    private String loadPromptFromFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[PromptService] failed to load prompt file: {}", path);
            return null;
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        promptCache.clear();
        log.info("[PromptService] cache cleared");
    }
}
