package com.lqragent.backend.agents.resource_generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.framework.Agent;
import com.lqragent.backend.framework.AgentIds;
import com.lqragent.backend.framework.AgentResult;
import com.lqragent.backend.framework.AgentTask;
import com.lqragent.backend.framework.ToolRegistry;
import com.lqragent.backend.framework.ToolSchema;
import com.lqragent.backend.agents.resource_generation.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resource_generation.service.ResourceGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 个性化教学资源生成智能体。
 * <p>
 * === 重构说明（Phase B）===
 *
 * <h3>1. System Prompt 升级</h3>
 * LLM 被要求研读学生画像特征，根据学生水平（入门/实操）自主决策资源生成顺序和类型。
 * 拒绝写死的并行派发。
 *
 * <h3>2. 工具执行器数据喂回重构</h3>
 * 工具执行器不再返回几千字的讲义/代码原文（resp.getContent()），
 * 而是返回标准 JSON 状态码：
 * {@code {"status":"success", "resourceId":128, "message":"讲义已生成并存入MySQL"}}
 * 完整内容已通过 ResourceGenerationService.generate() 写入 DB，
 * LLM 只需要知道「事情办完了」，从而切断 Token 放大。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceGenerationAgent implements Agent {

    private final ResourceGenerationService resourceFacadeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String agentId() { return AgentIds.RESOURCE_GENERATION; }

    @Override
    public AgentResult process(AgentTask task) {
        // LLM 未配置时的降级入口
        Map<String, Object> payload = task.getPayload();
        String kpId = (String) payload.getOrDefault("kpId", "");
        String resourceType = (String) payload.getOrDefault("resourceType", "LESSON");

        var request = ResourceGenerateRequest.builder().kpId(kpId).resourceType(resourceType).build();
        var response = resourceFacadeService.generate(request);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("resourceId", response.getResourceId()))
                .build();
    }

    // ================================================================
    //  [重构] System Prompt — 个性化教学资源生成专家
    //  LLM 必须研读学生画像，根据水平自主决策资源生成顺序
    // ================================================================
    @Override
    public String getSystemPrompt(AgentTask task) {
        String profileHint = extractProfileHint(task);
        return """
            你是 LQRAgent 的个性化教学资源生成专家。你面对的是一位有特定学习画像的学生。

            ## 你的角色
            你是资深计算机教育专家，擅长根据不同学生水平生成最合适的教学资源。
            不要一次性生成所有资源类型，而是根据学生情况逐步决策。

            ## 当前学生画像
            %s

            ## 资源生成决策规则

            ### 如果学生是「入门级 / 基础薄弱 / 无经验」
            1. 先调 generate_lesson 生成浅显易懂的讲义（多举例、少公式）
            2. 确认讲义生成成功后（收到 status=success），调 generate_quiz 生成基础练习题
            3. 根据学生反馈决定是否需要 generate_code_case

            ### 如果学生是「进阶级 / 有实操经验」
            1. 先调 generate_code_case 生成实战代码案例
            2. 确认成功后调 generate_extended_reading 生成拓展阅读
            3. 如果学生要求再补 generate_mind_map

            ### 如果学生水平未知
            1. 先调 generate_lesson 生成完整讲义
            2. 再调 generate_quiz 检验理解
            3. 最后视情况补 generate_code_case 或 generate_mind_map

            ## 可用工具
            每个工具生成指定类型的资源并持久化到 MySQL。
            调用后你会收到 JSON 状态码，根据 status 判断成功/失败。
            不要假设工具返回的内容中有完整讲义文本——工具只返回状态码。

            ## 重要规则
            - 一次只调用一个工具，确认成功后（status=success）再走下一步
            - 如果工具返回 status=error，告诉学生生成失败的原因，不要重试
            - 所有 5 种资源都生成完毕后，用自然语言汇总告知学生
            """.formatted(profileHint);
    }

    /** 从 task 中提取学生画像描述（供 System Prompt 使用） */
    private String extractProfileHint(AgentTask task) {
        Map<String, Object> payload = task.getPayload();
        if (payload == null) return "- 水平：未知（无画像数据）";

        // 尝试从 payload 中提取画像字段
        String level = (String) payload.getOrDefault("level", "");
        String goal = (String) payload.getOrDefault("goal", "");
        String interest = (String) payload.getOrDefault("interest", "");

        if (!level.isBlank()) {
            return "- 水平：" + level + "\n- 目标：" + (goal.isBlank() ? "未指定" : goal)
                    + "\n- 兴趣方向：" + (interest.isBlank() ? "未指定" : interest);
        }

        // 从 message 中推断
        String message = (String) payload.getOrDefault("message", "");
        if (message.contains("入门") || message.contains("基础")) {
            return "- 水平：入门级（根据用户输入推断）\n- 建议：优先生成浅显讲义和基础练习";
        }
        if (message.contains("进阶") || message.contains("实战") || message.contains("项目")) {
            return "- 水平：进阶级（根据用户输入推断）\n- 建议：优先生成代码案例和拓展阅读";
        }

        return "- 水平：未知\n- 策略：先生成完整讲义，再根据反馈调整";
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("generate_lesson", "生成讲义型资源（适合入门级学生，多举例讲解核心概念）",
                ToolSchema.params(Map.of(
                    "kpId", ToolSchema.stringParam("知识点ID", ""),
                    "kpTitle", ToolSchema.stringParam("知识点名称", "如：Python 装饰器")
                ), "kpId")),
            ToolSchema.of("generate_quiz", "生成练习题（选择+填空+编程题，适合检验理解）",
                ToolSchema.params(Map.of("kpId", ToolSchema.stringParam("知识点ID", ""), "kpTitle", ToolSchema.stringParam("知识点名称", "")), "kpId")),
            ToolSchema.of("generate_code_case", "生成代码案例（适合实操型学生，可运行的完整代码）",
                ToolSchema.params(Map.of("kpId", ToolSchema.stringParam("知识点ID", ""), "kpTitle", ToolSchema.stringParam("知识点名称", "")), "kpId")),
            ToolSchema.of("generate_mind_map", "生成思维导图（知识结构梳理，适合同一知识点的多轮复习）",
                ToolSchema.params(Map.of("kpId", ToolSchema.stringParam("知识点ID", ""), "kpTitle", ToolSchema.stringParam("知识点名称", "")), "kpId")),
            ToolSchema.of("generate_extended_reading", "生成拓展阅读（进阶知识、实践项目、常见误区）",
                ToolSchema.params(Map.of("kpId", ToolSchema.stringParam("知识点ID", ""), "kpTitle", ToolSchema.stringParam("知识点名称", "")), "kpId"))
        );
    }

    // ================================================================
    //  [重构] 工具执行器 — 只返回简短状态 JSON，不返回完整内容
    //  完整内容已由 ResourceGenerationService.generate() 写入 DB
    // ================================================================
    @Override
    public void registerTools(ToolRegistry registry) {
        // 5 种资源类型 → 5 个工具，每个都通过统一的执行逻辑
        Map<String, String> typeToTool = Map.of(
            "LESSON", "generate_lesson",
            "QUIZ", "generate_quiz",
            "CODE_CASE", "generate_code_case",
            "MIND_MAP", "generate_mind_map",
            "EXTENDED_READING", "generate_extended_reading"
        );

        Map<String, String> typeToLabel = Map.of(
            "LESSON", "讲义",
            "QUIZ", "练习题",
            "CODE_CASE", "代码案例",
            "MIND_MAP", "思维导图",
            "EXTENDED_READING", "拓展阅读"
        );

        for (var entry : typeToTool.entrySet()) {
            String resourceType = entry.getKey();
            String toolName = entry.getValue();
            String label = typeToLabel.get(resourceType);

            registry.register(agentId(), toolName, args -> {
                Map<String, Object> p = registry.parseArgs(args);
                String kpId = (String) p.getOrDefault("kpId", "");
                String kpTitle = (String) p.getOrDefault("kpTitle", "");

                var req = ResourceGenerateRequest.builder()
                        .kpId(kpId)
                        .resourceType(resourceType)
                        .build();

                // 调 Service → 完整的 LLM 生成 + DB 持久化
                var resp = resourceFacadeService.generate(req);

                // ════════════════════════════════════════════════
                //  [重构] 只返回短 JSON，不返回 resp.getContent()
                //  完整讲义/代码已通过 Service 写入 MySQL
                //  LLM 只需要知道「资源已生成，resourceId=xxx」
                // ════════════════════════════════════════════════
                String successMsg = String.format("「%s」%s 已成功生成并存入 MySQL 数据库", kpTitle, label);
                String hint;

                if (resourceType.equals("LESSON")) {
                    hint = "讲义已就绪。你可以询问学生是否需要做练习题来巩固理解 (generate_quiz)。";
                } else if (resourceType.equals("QUIZ")) {
                    hint = "习题已就绪。如果学生需要更深入的学习，建议先生成代码案例 (generate_code_case)。";
                } else if (resourceType.equals("CODE_CASE")) {
                    hint = "代码已就绪。建议接着生成思维导图帮助学生梳理结构 (generate_mind_map)。";
                } else {
                    hint = "资源已就绪。你可以继续为学生生成其他类型资源，或结束本轮生成。";
                }

                return Map.of(
                    "status", "success",
                    "message", successMsg,
                    "resourceId", resp.getResourceId(),
                    "resourceType", resourceType,
                    "next_step_hint", hint
                );
            });
        }
    }
}
