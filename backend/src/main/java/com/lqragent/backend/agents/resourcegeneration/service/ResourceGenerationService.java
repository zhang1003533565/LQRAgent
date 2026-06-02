package com.lqragent.backend.agents.resourcegeneration.service;

import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateResponse;
import com.lqragent.backend.agents.resourcegeneration.entity.ResourceItem;
import com.lqragent.backend.agents.resourcegeneration.repository.ResourceItemRepository;
import com.lqragent.backend.agents.qualityassessment.service.QualityAssessmentService;
import com.lqragent.backend.core.llm.LlmContentGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 资源生成门面服务。
 * 聚合 4 种资源类型（LESSON/QUIZ/CODE_CASE/ILLUSTRATION）的生成入口。
 * P1 实现：优先调 AiServer，失败或不可用时降级为模板内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceGenerationService {

    private final ResourceItemRepository resourceRepo;
    private final KnowledgeGraphService kgService;
    private final LlmContentGenerator llmGenerator;
    private final QualityAssessmentService qualityService;

    /**
     * 为某知识点生成指定类型的资源。
     */
    @Transactional
    public ResourceGenerateResponse generate(ResourceGenerateRequest request) {
        String rawKpId = request.getKpId();
        String type = request.getResourceType();

        // 空 kpId 防御：从 goal 模糊匹配
        if (rawKpId == null || rawKpId.isBlank()) {
            String goal = request.getCustomPrompt();
            if (goal != null && !goal.isBlank()) {
                rawKpId = resolveKpIdFromGoal(goal);
            }
            if (rawKpId == null || rawKpId.isBlank()) {
                throw new IllegalArgumentException("kpId 为空且无法从 goal 匹配知识点");
            }
        }
        final String kpId = rawKpId;

        KnowledgePoint kp = kgService.getByKpId(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        log.info("[ResourceFacade] generate: kp={}, type={}", kpId, type);

        // 查询已有资源数量
        List<ResourceItem> existing = resourceRepo.findByKpIdAndResourceType(kpId, type);
        int count = existing.size();

        // 按类型生成
        ResourceItem item = switch (type) {
            case ResourceItem.TYPE_LESSON -> generateLesson(kp, request);
            case ResourceItem.TYPE_QUIZ -> generateQuiz(kp, request);
            case ResourceItem.TYPE_CODE_CASE -> generateCodeCase(kp, request);
            case ResourceItem.TYPE_MIND_MAP -> generateMindMap(kp, request);
            case ResourceItem.TYPE_EXTENDED_READING -> generateExtendedReading(kp, request);
            case ResourceItem.TYPE_ILLUSTRATION -> generateIllustration(kp, request);
            default -> throw new IllegalArgumentException("不支持的资源类型: " + type);
        };

        item = resourceRepo.save(item);

        // 质量评估 + 重试
        var assessment = qualityService.assessFull(item);
        if (!assessment.passed()) {
            log.warn("[ResourceFacade] 首次质检未通过: id={}, failures={}, 重试中...", item.getId(), assessment.failures());
            Long failedId = item.getId();
            // 重新生成一次
            item = switch (type) {
                case ResourceItem.TYPE_LESSON -> generateLesson(kp, request);
                case ResourceItem.TYPE_QUIZ -> generateQuiz(kp, request);
                case ResourceItem.TYPE_CODE_CASE -> generateCodeCase(kp, request);
                case ResourceItem.TYPE_MIND_MAP -> generateMindMap(kp, request);
                case ResourceItem.TYPE_EXTENDED_READING -> generateExtendedReading(kp, request);
                case ResourceItem.TYPE_ILLUSTRATION -> generateIllustration(kp, request);
                default -> item;
            };
            item = resourceRepo.save(item);
            // 删除首次失败的记录
            resourceRepo.deleteById(failedId);
            log.info("[ResourceFacade] 重试完成: oldId={}, newId={}", failedId, item.getId());
        }

        log.info("[ResourceFacade] 已保存: id={}, type={}, kp={}", item.getId(), type, kpId);

        return ResourceGenerateResponse.builder()
                .resourceId(item.getId())
                .kpId(kpId)
                .resourceType(type)
                .title(item.getTitle())
                .content(item.getContent())
                .mediaUrl(item.getMediaUrl())
                .existingCount(count)
                .build();
    }

    /** 查询某知识点的所有资源 */
    @Transactional(readOnly = true)
    public List<ResourceItem> getByKpId(String kpId) {
        return resourceRepo.findByKpId(kpId);
    }

    // ===== 各类型生成器 =====

    /** 直接调 LLM API 生成，失败时返回模板 */
    private String callGenerateOrFallback(String type, String title, String description, String fallback) {
        String content = llmGenerator.generate(type, title, description);
        if (content != null) {
            log.info("[ResourceFacade] LLM 生成成功: type={}, title={}", type, title);
            return content;
        }
        log.info("[ResourceFacade] 使用模板兜底: type={}", type);
        return fallback;
    }

    private ResourceItem generateLesson(KnowledgePoint kp, ResourceGenerateRequest req) {
        String title = kp.getTitle() + " — 讲义";
        String template = """
## %s

### 学习目标
- 理解 %s 的核心概念
- 掌握 %s 的基本用法
- 能够独立完成相关练习

### 内容要点
1. **概念介绍**：%s
2. **基本语法**：
3. **示例代码**：
4. **注意事项**：

### 总结
%s 是 Python 编程中的重要知识点，建议配合代码练习加深理解。
""".formatted(kp.getTitle(), kp.getTitle(), kp.getTitle(), kp.getDescription(), kp.getTitle());
        String content = callGenerateOrFallback("lesson", kp.getTitle(), kp.getDescription(), template);

        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_LESSON)
                .title(title)
                .content(content)
                .build();
    }

    private ResourceItem generateQuiz(KnowledgePoint kp, ResourceGenerateRequest req) {
        String title = kp.getTitle() + " — 练习题";
        String template = """
### %s — 练习题

1. （选择题）关于 %s，以下说法正确的是？
   A) ...
   B) ...
   C) ...
   D) ...

2. （填空题）%s 的关键语法是________。

3. （编程题）请编写一段代码，演示 %s 的用法。

---
*提示：完成练习后可以对照参考资料检查答案。*
""".formatted(kp.getTitle(), kp.getTitle(), kp.getTitle(), kp.getTitle());
        String content = callGenerateOrFallback("quiz", kp.getTitle(), kp.getDescription(), template);

        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_QUIZ)
                .title(title)
                .content(content)
                .build();
    }

    private ResourceItem generateCodeCase(KnowledgePoint kp, ResourceGenerateRequest req) {
        String title = kp.getTitle() + " — 代码示例";
        String template = """
### %s — 示例代码

```python
# %s 用法演示
# ===========================


# 在此处编写示例代码


if __name__ == "__main__":
    # 运行示例
    pass
```

### 运行说明
1. 复制代码到 Python 环境
2. 运行观察输出
3. 修改参数体会不同行为
""".formatted(kp.getTitle(), kp.getTitle());
        String content = callGenerateOrFallback("code_case", kp.getTitle(), kp.getDescription(), template);

        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_CODE_CASE)
                .title(title)
                .content(content)
                .build();
    }

    private ResourceItem generateMindMap(KnowledgePoint kp, ResourceGenerateRequest req) {
        String title = kp.getTitle() + " — 思维导图";
        String content = callGenerateOrFallback("mind_map", kp.getTitle(), kp.getDescription(),
                "- " + kp.getTitle()
                + "\n  - 核心概念\n    - " + kp.getDescription());
        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_MIND_MAP)
                .title(title)
                .content(content)
                .generationPrompt(req.getCustomPrompt())
                .build();
    }

    private ResourceItem generateExtendedReading(KnowledgePoint kp, ResourceGenerateRequest req) {
        String title = kp.getTitle() + " — 拓展阅读";
        String content = callGenerateOrFallback("extended_reading", kp.getTitle(), kp.getDescription(),
                "### " + kp.getTitle() + " — 拓展阅读\n\n"
                + "- 推荐阅读官方文档关于 " + kp.getTitle() + " 的部分\n"
                + "- 尝试在项目中运用该知识点\n"
                + "- 参考相关开源代码示例");
        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_EXTENDED_READING)
                .title(title)
                .content(content)
                .generationPrompt(req.getCustomPrompt())
                .build();
    }

    private ResourceItem generateIllustration(KnowledgePoint kp, ResourceGenerateRequest req) {
        String title = kp.getTitle() + " — 示意图";
        // P1: 返回占位内容，P1-8 的 MediaGenerationService 会替换这里的实现
        String content = "### " + kp.getTitle() + " — 示意图\n\n<!-- 示意图占位，后续由 MediaGenerationService 生成 -->\n\n![占位示意图](PLACEHOLDER)";

        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_ILLUSTRATION)
                .title(title)
                .content(content)
                .generationPrompt(req.getCustomPrompt())
                .build();
    }

    /** 从 goal 文本模糊匹配知识点ID */
    private String resolveKpIdFromGoal(String goal) {
        if (goal == null || goal.isBlank()) return null;
        if (goal.matches("kp_[a-zA-Z0-9_]+")) return goal;
        List<KnowledgePoint> all = kgService.getAll();
        for (KnowledgePoint kp : all) {
            if (kp.getTitle().contains(goal) || goal.contains(kp.getTitle())) {
                return kp.getKpId();
            }
        }
        for (KnowledgePoint kp : all) {
            if (kp.getDescription() != null && kp.getDescription().contains(goal)) {
                return kp.getKpId();
            }
        }
        return null;
    }
}
