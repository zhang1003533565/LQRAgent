package com.lqragent.backend.agents.qualityassessment.service;

import com.lqragent.backend.agents.resourcegeneration.entity.ResourceItem;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源质量评估服务。
 * 包含 4 层检查：格式 → LLM 自检 → 敏感词 → 学术规范。
 * 可被 QualityAssessmentAgent 调用，失败可触发重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityAssessmentService {

    private final AiServerWsProxy aiServerWsProxy;
    private final SensitiveFilter sensitiveFilter;
    private final AcademicChecker academicChecker;
    private final AppRuntimeConfig runtimeConfig;

    /** 全量检查结果 */
    public record AssessmentResult(boolean passed, List<String> failures, String summary) {
        public static AssessmentResult pass() {
            return new AssessmentResult(true, List.of(), "全部检查通过");
        }
        public static AssessmentResult fail(List<String> failures) {
            return new AssessmentResult(false, failures, "未通过: " + String.join("; ", failures));
        }
    }

    /**
     * 全量评估资源质量（格式 + LLM + 敏感词 + 学术规范）。
     * 从 sys_config 读取各开关，未启用则跳过对应检查。
     */
    public AssessmentResult assessFull(ResourceItem item) {
        List<String> failures = new ArrayList<>();
        if (item == null) return AssessmentResult.fail(List.of("资源为空"));

        // 1. 基础格式检查（始终开启）
        if (item.getContent() == null || item.getContent().isBlank())
            failures.add("内容为空");
        else if (item.getContent().length() < 10)
            failures.add("内容过短(" + item.getContent().length() + "字符)");
        if (item.getTitle() == null || item.getTitle().isBlank())
            failures.add("标题为空");

        // 2. LLM 事实性校验（由 sys_config 开关控制，默认关闭）
        if (failures.isEmpty() && Boolean.parseBoolean(runtimeConfig.get("agent.quality.llm_check", "false"))) {
            String llmResult = aiServerWsProxy.qualityCheck(item.getTitle(), item.getContent());
            if (llmResult != null && llmResult.startsWith("FAIL")) {
                failures.add("事实校验: " + llmResult);
            }
        }

        // 3. 敏感内容过滤
        var sensitiveResult = sensitiveFilter.check(item.getContent());
        if (!sensitiveResult.passed()) {
            failures.add("敏感内容: " + sensitiveResult.reason());
        }

        // 4. 学术规范性检查
        var academicResult = academicChecker.check(item.getContent());
        if (!academicResult.passed()) {
            failures.add("学术规范: " + academicResult.reason());
        }

        if (failures.isEmpty()) {
            log.info("[QualityAssessment] 全量检查通过: id={}, type={}", item.getId(), item.getResourceType());
            return AssessmentResult.pass();
        }
        log.warn("[QualityAssessment] 检查未通过: id={}, failures={}", item.getId(), failures);
        return AssessmentResult.fail(failures);
    }

    /**
     * 简版评估（向后兼容，仅格式检查）。
     */
    public boolean assess(ResourceItem item) {
        return assessFull(item).passed();
    }

    public ResourceItem buildFallback(ResourceItem original, String reason) {
        original.setContent("【内容生成质量不合格 - " + reason + "】请稍后重试或联系管理员。");
        log.info("[QualityAssessment] 降级替换: id={}, reason={}", original.getId(), reason);
        return original;
    }
}
