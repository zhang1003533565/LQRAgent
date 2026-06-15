package com.lqragent.backend.agents.resourcegeneration.service;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateResponse;
import com.lqragent.backend.agents.resourcegeneration.entity.ResourceItem;
import com.lqragent.backend.agents.resourcegeneration.repository.ResourceItemRepository;
import com.lqragent.backend.agents.qualityassessment.service.QualityAssessmentService;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
    private final AiServerWsProxy aiServerWsProxy;
    private final AppRuntimeConfig runtimeConfig;
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

        // 查询关联知识点（前置+后置）
        List<String> relatedKpIds = new java.util.ArrayList<>();
        try {
            List<String> prereqs = kgService.getPrerequisites(kpId).stream()
                    .map(p -> p.getKpId()).collect(Collectors.toList());
            List<String> dependents = kgService.getDependents(kpId).stream()
                    .map(p -> p.getKpId()).collect(Collectors.toList());
            relatedKpIds.addAll(prereqs);
            relatedKpIds.addAll(dependents);
        } catch (Exception e) {
            log.warn("[ResourceFacade] 查询关联知识点失败: {}", e.getMessage());
        }

        return ResourceGenerateResponse.builder()
                .resourceId(item.getId())
                .kpId(kpId)
                .resourceType(type)
                .title(item.getTitle())
                .content(item.getContent())
                .mediaUrl(item.getMediaUrl())
                .existingCount(count)
                .relatedKpIds(relatedKpIds)
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
        String content = aiServerWsProxy.generateResource(type, title, description);
        if (content != null) {
            log.info("[ResourceFacade] LLM 生成成功(via ai-server): type={}, title={}", type, title);
            return content;
        }
        log.info("[ResourceFacade] ai-server 不可用，使用模板兜底: type={}", type);
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
        String prompt = req.getCustomPrompt() != null ? req.getCustomPrompt() : kp.getTitle() + " 概念示意图，教学用，清晰简洁";
        
        String provider = runtimeConfig.get("agent.mediagen.image_provider", "mock");
        String imageUrl;
        String mime;
        
        switch (provider) {
            case "dalle3" -> {
                imageUrl = callDalle3(prompt);
                mime = "image/png";
            }
            case "sd3" -> {
                imageUrl = callStableDiffusion(prompt);
                mime = "image/webp";
            }
            default -> {
                imageUrl = buildPlaceholderSvg(kp.getTitle());
                mime = "image/svg+xml";
            }
        }
        
        log.info("[ResourceGeneration] 示意图生成: kpId={}, provider={}", kp.getKpId(), provider);

        return ResourceItem.builder()
                .kpId(kp.getKpId())
                .resourceType(ResourceItem.TYPE_ILLUSTRATION)
                .title(title)
                .content("### " + title + "\n\n![示意图](" + imageUrl + ")")
                .mediaUrl(imageUrl)
                .mediaMime(mime)
                .generationPrompt(prompt)
                .build();
    }
    
    private String callDalle3(String prompt) {
        String apiKey = runtimeConfig.get("llm.api-key", "");
        if (apiKey.isBlank()) {
            log.warn("[ResourceGeneration] DALL-E API Key 未配置");
            return buildPlaceholderSvg("API Key 未配置");
        }
        try {
            var client = org.springframework.web.client.RestClient.builder().build();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resp = client.post()
                    .uri("https://api.openai.com/v1/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("model", "dall-e-3", "prompt", prompt, "n", 1, "size", "1024x1024"))
                    .retrieve()
                    .body(java.util.Map.class);
            if (resp != null) {
                @SuppressWarnings("unchecked")
                var data = (java.util.List<java.util.Map<String, Object>>) resp.get("data");
                if (data != null && !data.isEmpty()) {
                    String url = (String) data.get(0).get("url");
                    if (url != null) return url;
                }
            }
        } catch (Exception e) {
            log.warn("[ResourceGeneration] DALL-E 调用失败: {}", e.getMessage());
        }
        return buildPlaceholderSvg("DALL-E 生成失败");
    }
    
    private String callStableDiffusion(String prompt) {
        String apiKey = runtimeConfig.get("agent.mediagen.api_key", "");
        if (apiKey.isBlank()) {
            return buildPlaceholderSvg("API Key 未配置");
        }
        try {
            var client = org.springframework.web.client.RestClient.builder().build();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resp = client.post()
                    .uri("https://api.stability.ai/v2beta/stable-image/generate/ultra")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("prompt", prompt, "output_format", "webp"))
                    .retrieve()
                    .body(java.util.Map.class);
            if (resp != null && resp.containsKey("image")) {
                return "data:image/webp;base64," + resp.get("image");
            }
        } catch (Exception e) {
            log.warn("[ResourceGeneration] Stability AI 调用失败: {}", e.getMessage());
        }
        return buildPlaceholderSvg("Stability AI 生成失败");
    }
    
    private String buildPlaceholderSvg(String label) {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"300\" viewBox=\"0 0 400 300\">"
                + "<rect width=\"400\" height=\"300\" rx=\"16\" fill=\"#eef2f7\"/>"
                + "<text x=\"200\" y=\"150\" text-anchor=\"middle\" font-family=\"sans-serif\" font-size=\"16\" fill=\"#526989\">" + label + "</text>"
                + "</svg>";
        return "data:image/svg+xml," + java.net.URLEncoder.encode(svg, java.nio.charset.StandardCharsets.UTF_8);
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
