package com.lqragent.backend.agents.learn.path.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lqragent.backend.agents.learn.path.dto.LearningPathDto;
import com.lqragent.backend.agents.learn.path.entity.LearningPath;
import com.lqragent.backend.agents.learn.path.entity.LearningPathStep;
import com.lqragent.backend.agents.learn.path.repository.LearningPathRepository;
import com.lqragent.backend.agents.learn.path.repository.LearningPathStepRepository;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileSummaryDto;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 学习路径规划服务。
 * 流程：解析目标 → 图谱算最短路径 → 持久化 → 返回真实知识点节点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathService {

    private final KnowledgeGraphService kgService;
    private final LearningPathRepository pathRepo;
    private final LearningPathStepRepository stepRepo;
    private final AiServerWsProxy aiServerWsProxy;
    private final AppRuntimeConfig runtimeConfig;
    private final LearnerProfileService profileService;

    /**
     * 生成新学习路径。
     *
     * @param userId      当前用户ID
     * @param goal        学习目标，可以是知识点ID (kp_xxx) 或自然语言（如"装饰器"）
     * @param currentKpId 当前知识点ID（可选，留空从图谱开头算起）
     * @return 学习路径 DTO
     */
    @Transactional
    public LearningPathDto generatePath(Long userId, String goal, String currentKpId) {
        // 1. 解析目标 → 找到所有匹配的知识点ID（ai-server 不可用时降级到动态生成）
        List<String> allTargetKpIds;
        try {
            allTargetKpIds = llmResolveKpIds(goal);
            log.info("[LearningPath] generatePath: userId={}, goal={}, targets={}", userId, goal, allTargetKpIds);
        } catch (Exception e) {
            log.warn("[LearningPath] llmResolveKpIds failed (ai-server likely unavailable), falling back to dynamic: {}", e.getMessage());
            allTargetKpIds = List.of();
        }

        // 如果没有匹配到已有知识点，使用LLM动态生成路径
        if (allTargetKpIds.isEmpty()) {
            log.info("[LearningPath] 无匹配知识点，使用LLM动态生成: goal={}", goal);
            return generateDynamicPath(userId, goal);
        }

        String targetKpId = allTargetKpIds.get(allTargetKpIds.size() - 1);

        // 2. 合并所有目标的拓扑路径
        List<String> pathKpIds = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (String targetId : allTargetKpIds) {
            List<String> topoPath = kgService.topologicalPathTo(targetId);
            for (String kpId : topoPath) {
                if (seen.add(kpId)) {
                    pathKpIds.add(kpId);
                }
            }
        }

        if (pathKpIds.isEmpty()) {
            log.warn("[LearningPath] 拓扑排序失败，使用 BFS 兜底");
            String fromKpId = currentKpId != null && !currentKpId.isBlank() ? currentKpId : findFirstKpId();
            for (String targetId : allTargetKpIds) {
                List<String> bfsPath = kgService.findShortestPath(fromKpId, targetId);
                for (String kpId : bfsPath) {
                    if (seen.add(kpId)) {
                        pathKpIds.add(kpId);
                    }
                }
                if (!bfsPath.isEmpty()) fromKpId = bfsPath.get(bfsPath.size() - 1);
            }
        }

        // 4. 构建 DTO
        List<LearningPathDto.PathNode> nodes = buildNodes(pathKpIds);

        // 5. LLM 个性化排序（可选，失败保留原顺序）
        nodes = sortNodesWithLlm(nodes, userId);

        String planDescription = buildPlanDescription(nodes);

        // 5. 持久化到 DB
        LearningPath path = LearningPath.builder()
                .userId(userId)
                .goal(goal)
                .planDescription(planDescription)
                .status("ACTIVE")
                .build();
        path = pathRepo.save(path);

        final Long pathId = path.getId();
        List<LearningPathStep> steps = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            LearningPathDto.PathNode node = nodes.get(i);
            steps.add(LearningPathStep.builder()
                    .pathId(pathId)
                    .kpId(node.getKpId())
                    .stepOrder(node.getOrder())
                    .completed(false)
                    .status("PENDING")
                    .build());
        }
        stepRepo.saveAll(steps);
        log.info("[LearningPath] 路径已保存: id={}, steps={}", pathId, steps.size());

        return LearningPathDto.builder()
                .pathId(pathId)
                .goal(goal)
                .nodes(nodes)
                .planDescription(planDescription)
                .build();
    }

    /**
     * 更新学习路径步骤状态
     * @param pathId 路径ID
     * @param kpId 知识点ID
     * @param completed 是否完成
     * @param status 步骤状态：PENDING/ACTIVE/COMPLETED/SKIPPED
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateStepStatus(Long pathId, String kpId, boolean completed, String status) {
        Optional<LearningPathStep> optional = stepRepo.findByPathIdAndKpId(pathId, kpId);
        if (optional.isEmpty()) {
            log.warn("[LearningPath] step not found: pathId={}, kpId={}", pathId, kpId);
            return false;
        }
        LearningPathStep step = optional.get();
        step.setCompleted(completed);
        step.setStatus(status);
        if (completed) {
            step.setCompletedAt(java.time.LocalDateTime.now());
        }
        stepRepo.save(step);
        log.info("[LearningPath] step updated: pathId={}, kpId={}, completed={}, status={}", pathId, kpId, completed, status);
        return true;
    }

    /** 查询当前用户最近一条 ACTIVE 路径 */
    @Transactional(readOnly = true)
    public Optional<LearningPathDto> getCurrentPath(Long userId) {
        return pathRepo.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE")
                .map(path -> {
                    List<LearningPathStep> steps = stepRepo.findByPathIdOrderByStepOrder(path.getId());
                    List<LearningPathDto.PathNode> nodes = steps.stream()
                            .map(s -> {
                                String title = kgService.getByKpId(s.getKpId())
                                        .map(KnowledgePoint::getTitle)
                                        .orElse(s.getKpId());
                                return LearningPathDto.PathNode.builder()
                                        .kpId(s.getKpId())
                                        .title(title)
                                        .order(s.getStepOrder())
                                        .completed(s.getCompleted())
                                        .status(s.getStatus())
                                        .build();
                            })
                            .collect(Collectors.toList());
                    return LearningPathDto.builder()
                            .pathId(path.getId())
                            .goal(path.getGoal())
                            .nodes(nodes)
                            .planDescription(path.getPlanDescription())
                            .build();
                });
    }

    // ===== internal =====

    /** 把自然语言或 kp_id 解析为知识点ID */
    private String resolveKpId(String goal) {
        // 如果直接是 kp_xxx 格式，直接返回
        if (goal != null && goal.matches("kp_[a-zA-Z0-9_]+")) {
            return goal;
        }
        List<KnowledgePoint> all = kgService.getAll();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("知识图谱为空，无法规划学习路径");
        }
        if (goal == null || goal.isBlank()) {
            return all.get(0).getKpId();
        }

        // 1. 精确包含匹配（goal 包含标题 或 标题包含 goal）
        for (KnowledgePoint kp : all) {
            if (kp.getTitle().contains(goal) || goal.contains(kp.getTitle())) {
                return kp.getKpId();
            }
        }

        // 2. 双向子串匹配：goal 中的词 vs 知识点标题中的词，双向都能命中
        //    "我想学for循环" 能命中 "for循环与range"
        //    "异常处理怎么学" 能命中 "异常处理 try/except"
        //    "类和对象" 能命中 "类与对象"
        String goalLower = goal.toLowerCase();
        String bestKpId = null;
        int bestScore = 0;
        for (KnowledgePoint kp : all) {
            String titleLower = kp.getTitle().toLowerCase();
            String descLower = kp.getDescription() != null ? kp.getDescription().toLowerCase() : "";
            int score = 0;

            // 策略 A：goal 包含标题 → 精确命中（最高分）
            if (goalLower.contains(titleLower)) {
                score += 100;
            }
            // 策略 B：标题包含 goal → 也是精确命中
            if (titleLower.contains(goalLower)) {
                score += 100;
            }

            // 策略 C：从标题中提取核心词（去掉空格/标点后的连续中文或英文），检查 goal 是否包含
            java.util.regex.Matcher tm = java.util.regex.Pattern.compile("[\u4e00-\u9fff]+|[a-zA-Z_][a-zA-Z0-9_]*").matcher(titleLower);
            while (tm.find()) {
                String titleWord = tm.group();
                if (titleWord.length() >= 2 && goalLower.contains(titleWord)) {
                    score += 10; // 标题词被 goal 包含
                }
            }

            // 策略 D：从 goal 中提取核心词，检查标题是否包含
            java.util.regex.Matcher gm = java.util.regex.Pattern.compile("[\u4e00-\u9fff]{2,}|[a-zA-Z_][a-zA-Z0-9_]*").matcher(goalLower);
            while (gm.find()) {
                String goalWord = gm.group();
                // 跳过通用词
                if (Set.of("学习", "学会", "我想", "帮我", "复习", "学一学", "了解一下", "什么是", "怎么用", "怎么学", "我想学").contains(goalWord)) {
                    continue;
                }
                if (goalWord.length() >= 2 && titleLower.contains(goalWord)) {
                    score += 5; // goal 词出现在标题中
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestKpId = kp.getKpId();
            }
        }
        // 策略 E：中文子串拆分 — "生成器和迭代器" 拆出 "生成器"+"迭代器" 匹配 "生成器与迭代器"
        if (bestScore == 0) {
            // 从 goal 中提取中文片段，再按常见连接词拆分
            java.util.regex.Matcher cm = java.util.regex.Pattern.compile("[\u4e00-\u9fff]{2,}").matcher(goalLower);
            while (cm.find()) {
                String chunk = cm.group();
                // 按 "和" "与" "及" "以及" "、" 拆分
                String[] parts = chunk.split("[和与及及、]");
                for (String part : parts) {
                    if (part.length() < 2) continue;
                    for (KnowledgePoint kp : all) {
                        String titleLower2 = kp.getTitle().toLowerCase();
                        if (titleLower2.contains(part)) {
                            int s = 5;
                            if (s > bestScore) {
                                bestScore = s;
                                bestKpId = kp.getKpId();
                            }
                        }
                    }
                }
            }
        }

        if (bestKpId != null && bestScore > 0) {
            log.info("[LearningPath] 匹配成功: goal={}, bestScore={}, bestKpId={}", goal, bestScore, bestKpId);
            return bestKpId;
        }

        // 3. 描述全文匹配
        for (KnowledgePoint kp : all) {
            if (kp.getDescription() != null && kp.getDescription().contains(goal)) {
                return kp.getKpId();
            }
        }

        // 4. 兜底：返回第一个知识点
        log.warn("[LearningPath] 未匹配到知识点, goal={}, 使用第一个KP: {}", goal, all.get(0).getKpId());
        return all.get(0).getKpId();
    }

    /**
     * 从目标文本中解析出所有匹配的知识点ID列表（按得分降序）。
     * 支持多目标：如"学习列表和字典" → [kp_list, kp_dict]
     */
        /**
     * 调用 ai-server LLM 理解用户意图，从知识图谱中选出匹配的知识点。
     * 支持多目标：如"学习装饰器和生成器" → [kp_decorator, kp_generator]
     * LLM 失败时降级到关键词匹配。
     */
    private List<String> llmResolveKpIds(String goal) {
        List<KnowledgePoint> all = kgService.getAll();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("知识图谱为空，无法规划学习路径");
        }
        if (goal == null || goal.isBlank()) {
            return List.of(all.get(0).getKpId());
        }
        if (goal.matches("kp_[a-zA-Z0-9_]+")) {
            return List.of(goal);
        }

        // 拼接知识点列表供 LLM 参考
        StringBuilder kpList = new StringBuilder();
        for (KnowledgePoint kp : all) {
            kpList.append(kp.getKpId()).append(": ").append(kp.getTitle());
            if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
                kpList.append(" — ").append(kp.getDescription(), 0, Math.min(kp.getDescription().length(), 30));
            }
            kpList.append("\n");
        }

        // 精简 prompt，避免 LLM 当成生成任务
        String prompt = "从下面的知识点列表中，选出和「" + goal + "」相关的知识点ID，用逗号分隔，不要解释。\n\n"
                + kpList + "\n"
                + "只输出ID，例如：kp_class,kp_inheritance,kp_polymorphism";

        try {
            // 直接调 DeepSeek API，完全控制 system prompt
            String apiKey = runtimeConfig.get("llm.api-key", "");
            String baseUrl = runtimeConfig.get("llm.host", "https://api.deepseek.com");
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            
            String reqBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                java.util.Map.of(
                    "model", runtimeConfig.get("llm.model", "deepseek-chat"),
                    "messages", java.util.List.of(
                        java.util.Map.of("role", "system", "content", "你是Python课程学习规划助手。只输出逗号分隔的知识点ID列表，不要任何解释。"),
                        java.util.Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 200,
                    "temperature", 0.1
                )
            );
            
            var httpClient = org.springframework.web.client.RestClient.builder().build();
            String respJson = httpClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(reqBody)
                .retrieve()
                .body(String.class);
            
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(respJson);
            String result = root.path("choices").path(0).path("message").path("content").asText("");
            if (result != null && !result.isBlank()) {
                // 清理 LLM 输出，提取 kp_id
                result = result.replaceAll("```[a-z]*", "").replaceAll("```", "").trim();
                List<String> ids = new java.util.ArrayList<>();
                for (String id : result.split("[,，\\s\n]+")) {
                    String trimmed = id.trim().replaceAll("[^a-zA-Z0-9_]", "");
                    if (trimmed.startsWith("kp_") && all.stream().anyMatch(kp -> kp.getKpId().equals(trimmed))) {
                        if (!ids.contains(trimmed)) {
                            ids.add(trimmed);
                        }
                    }
                }
                // 如果匹配到的知识点数量合理（不超过总数的50%），才认为是有效匹配
                if (!ids.isEmpty() && ids.size() <= all.size() / 2) {
                    log.info("[LearningPath] LLM匹配成功: goal={}, targets={}", goal, ids);
                    return ids;
                }
                log.warn("[LearningPath] LLM匹配结果过多或为空: goal={}, ids={}", goal, ids);
                log.warn("[LearningPath] LLM返回无法解析: {}", result);
            }
        } catch (Exception e) {
            log.warn("[LearningPath] LLM调用失败，降级到关键词匹配: {}", e.getMessage());
        }

        // 如果LLM没有匹配到，返回空列表（触发动态生成）
        log.info("[LearningPath] 无匹配知识点，将使用动态生成: goal={}", goal);
        return java.util.List.of();
    }

private List<String> resolveAllKpIds(String goal) {
        List<KnowledgePoint> all = kgService.getAll();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("知识图谱为空，无法规划学习路径");
        }
        if (goal == null || goal.isBlank()) {
            return List.of(all.get(0).getKpId());
        }
        if (goal.matches("kp_[a-zA-Z0-9_]+")) {
            return List.of(goal);
        }

        String goalLower = goal.toLowerCase();
        java.util.TreeMap<Integer, java.util.List<String>> scoreMap = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());

        for (KnowledgePoint kp : all) {
            String titleLower = kp.getTitle().toLowerCase();
            int score = 0;

            if (goalLower.contains(titleLower) || titleLower.contains(goalLower)) {
                score += 100;
            }

            java.util.regex.Matcher tm = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]+|[a-zA-Z_][a-zA-Z0-9_]*").matcher(titleLower);
            while (tm.find()) {
                String w = tm.group();
                if (w.length() >= 2 && goalLower.contains(w)) score += 10;
            }

            java.util.regex.Matcher gm = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]{2,}|[a-zA-Z_][a-zA-Z0-9_]*").matcher(goalLower);
            while (gm.find()) {
                String w = gm.group();
                if (Set.of("学习","学会","我想","帮我","复习","基础","语法","包括","以及","和","与","的").contains(w)) continue;
                if (w.length() >= 2 && titleLower.contains(w)) score += 5;
            }

            if (score > 0) scoreMap.computeIfAbsent(score, k -> new java.util.ArrayList<>()).add(kp.getKpId());
        }

        List<String> result = new java.util.ArrayList<>();
        for (Map.Entry<Integer, java.util.List<String>> entry : scoreMap.entrySet()) result.addAll(entry.getValue());

        if (!result.isEmpty()) {
            log.info("[LearningPath] resolveAllKpIds: goal={}, matched={}: {}", goal, result.size(), result);
            return result;
        }
        log.warn("[LearningPath] 未匹配到知识点, goal={}", goal);
        return List.of(all.get(0).getKpId());
    }

    /** 获取图谱第一个知识点（作为默认起点） */
    private String findFirstKpId() {
        List<KnowledgePoint> all = kgService.getAll();
        return all.isEmpty() ? "kp_intro" : all.get(0).getKpId();
    }

    /** kp_id 列表 → DTO 节点列表 */
    private List<LearningPathDto.PathNode> buildNodes(List<String> kpIds) {
        List<LearningPathDto.PathNode> nodes = new ArrayList<>();
        for (int i = 0; i < kpIds.size(); i++) {
            String kpId = kpIds.get(i);
            KnowledgePoint kp = kgService.getByKpId(kpId).orElse(null);
            nodes.add(LearningPathDto.PathNode.builder()
                    .kpId(kpId)
                    .title(kp != null ? kp.getTitle() : kpId)
                    .description(kp != null ? kp.getDescription() : "")
                    .order(i)
                    .completed(false)
                    .status("PENDING")
                    .build());
        }
        return nodes;
    }

    /**
     * 调 LLM 对路径节点做个性化排序。失败时保留原顺序。
     */
    private List<LearningPathDto.PathNode> sortNodesWithLlm(List<LearningPathDto.PathNode> nodes, Long userId) {
        if (nodes.size() <= 1) return nodes;

        try {
            ProfileSummaryDto profile = profileService.getSummary(userId);
            String profileHint = buildProfileHint(profile);

            List<String> kpIds = nodes.stream().map(LearningPathDto.PathNode::getKpId).toList();
            List<String> sorted = parsePathSortResult(aiServerWsProxy.sortPath(kpIds, profileHint));

            if (sorted != null && !sorted.isEmpty()) {
                // 按 LLM 返回的顺序重建节点，跳过不在原路径中的 ID
                Map<String, LearningPathDto.PathNode> nodeMap = new LinkedHashMap<>();
                for (LearningPathDto.PathNode n : nodes) {
                    nodeMap.put(n.getKpId(), n);
                }
                List<LearningPathDto.PathNode> result = new ArrayList<>();
                int order = 0;
                for (String kpId : sorted) {
                    LearningPathDto.PathNode node = nodeMap.remove(kpId);
                    if (node != null) {
                        result.add(node.toBuilder().order(order++).build());
                    }
                }
                // 追加 LLM 未提及的节点（兜底）
                for (LearningPathDto.PathNode remaining : nodeMap.values()) {
                    result.add(remaining.toBuilder().order(order++).build());
                }
                log.info("[LearningPath] LLM 排序完成: {} 个节点", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("[LearningPath] LLM 排序失败，保留原顺序: {}", e.getMessage());
        }
        return nodes;
    }

    private String buildProfileHint(ProfileSummaryDto profile) {
        if (profile == null) return "无画像数据";
        StringBuilder sb = new StringBuilder();
        sb.append("- 知识水平: ").append(profile.getKnowledgeLevel() != null ? profile.getKnowledgeLevel() : "未知").append("\n");
        sb.append("- 学习目标: ").append(profile.getLearningGoal() != null ? profile.getLearningGoal() : "未指定").append("\n");
        sb.append("- 学习节奏: ").append(profile.getLearningPace() != null ? profile.getLearningPace() : "未知").append("\n");
        sb.append("- 兴趣方向: ").append(profile.getInterestDirection() != null ? profile.getInterestDirection() : "未指定").append("\n");
        sb.append("- 已掌握知识点: ").append(profile.getTopicMastery() != null ? profile.getTopicMastery() : "无数据").append("\n");
        return sb.toString();
    }

    private String buildPlanDescription(List<LearningPathDto.PathNode> nodes) {
        if (nodes.isEmpty()) return "暂无学习计划";
        StringBuilder sb = new StringBuilder("学习路线：");
        for (int i = 0; i < nodes.size(); i++) {
            sb.append(nodes.get(i).getTitle());
            if (i < nodes.size() - 1) sb.append(" → ");
        }
        return sb.toString();
    }

    /**
     * 解析 ai-server 返回的路径排序结果（JSON 数组字符串）。
     * 失败返回 null，调用方保留原顺序。
     */
    private List<String> parsePathSortResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // 提取 JSON 数组（LLM 可能返回额外文字）
            String trimmed = raw.strip();
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> sorted = mapper.readValue(trimmed,
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (sorted != null && !sorted.isEmpty()) {
                log.info("[LearningPath] 路径排序成功(via ai-server): {} 个节点", sorted.size());
                return sorted;
            }
        } catch (Exception e) {
            log.warn("[LearningPath] 路径排序结果解析失败: {}", e.getMessage());
        }
        return null;
    }



    /**
     * 动态生成学习路径（当目标不在知识图谱中时）
     * 使用LLM直接生成学习步骤，不依赖已有知识图谱
     */
    private LearningPathDto generateDynamicPath(Long userId, String goal) {
        log.info("[LearningPath] 动态生成路径: userId={}, goal={}", userId, goal);

        // 使用LLM生成学习路径
        String systemPrompt = "你是一个学习路径规划专家。根据用户的学习目标，生成一个结构化的学习路径。\n" +
            "返回JSON格式，包含以下字段：\n" +
            "- nodes: 学习节点数组，每个节点包含 title, description, order\n" +
            "- planDescription: 整体学习计划说明\n" +
            "节点数量控制在5-10个，每个节点应该是一个具体的学习阶段。\n" +
            "只返回JSON，不要其他内容。";

        String userPrompt = "请为以下学习目标生成学习路径：\n" + goal;

        try {
            String apiKey = runtimeConfig.get("llm.api-key", "");
            String baseUrl = runtimeConfig.get("llm.host", "https://api.deepseek.com");
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

            String reqBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                java.util.Map.of(
                    "model", runtimeConfig.get("llm.model", "deepseek-chat"),
                    "messages", java.util.List.of(
                        java.util.Map.of("role", "system", "content", systemPrompt),
                        java.util.Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", 1500,
                    "temperature", 0.3
                )
            );

            // 配置带超时的 RestClient
            var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(15));
            requestFactory.setReadTimeout(java.time.Duration.ofSeconds(90));
            
            var httpClient = org.springframework.web.client.RestClient.builder()
                .requestFactory(requestFactory)
                .build();
            
            String respJson = httpClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(reqBody)
                .retrieve()
                .body(String.class);

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(respJson);
            String result = root.path("choices").path(0).path("message").path("content").asText("");

            if (result != null && !result.isBlank()) {
                // 清理JSON
                result = result.replaceAll("```json", "").replaceAll("```", "").trim();
                log.info("[LearningPath] LLM返回的原始结果: {}", result);

                var resultNode = mapper.readTree(result);
                log.info("[LearningPath] LLM返回的JSON结构: {}", resultNode.toString());
                
                var nodesArray = resultNode.path("nodes");
                if (nodesArray.isMissingNode() || !nodesArray.isArray()) {
                    // 尝试其他可能的字段名
                    nodesArray = resultNode.path("steps");
                    if (nodesArray.isMissingNode() || !nodesArray.isArray()) {
                        nodesArray = resultNode.path("path");
                    }
                }
                log.info("[LearningPath] 解析到节点数组: {}", nodesArray);
                
                String planDesc = resultNode.path("planDescription").asText(null);
                if (planDesc == null || planDesc.isBlank()) {
                    planDesc = resultNode.path("description").asText("个性化学习路径");
                }

                List<LearningPathDto.PathNode> nodes = new ArrayList<>();
                for (int i = 0; i < nodesArray.size(); i++) {
                    var node = nodesArray.get(i);
                    String nodeId = "dynamic_" + System.currentTimeMillis() + "_" + i;
                    
                    // 尝试多种可能的字段名
                    String title = node.path("title").asText(null);
                    if (title == null || title.isBlank()) {
                        title = node.path("name").asText(null);
                    }
                    if (title == null || title.isBlank()) {
                        title = node.path("step").asText(null);
                    }
                    if (title == null || title.isBlank()) {
                        title = "学习阶段 " + (i + 1);
                    }
                    
                    String desc = node.path("description").asText(null);
                    if (desc == null || desc.isBlank()) {
                        desc = node.path("desc").asText(null);
                    }
                    if (desc == null || desc.isBlank()) {
                        desc = node.path("content").asText(null);
                    }
                    if (desc == null || desc.isBlank()) {
                        desc = "学习" + title + "的相关知识";
                    }
                    
                    nodes.add(LearningPathDto.PathNode.builder()
                            .kpId(nodeId)
                            .title(title)
                            .description(desc)
                            .order(i)
                            .completed(false)
                            .status("PENDING")
                            .build());
                }

                // 持久化
                LearningPath path = LearningPath.builder()
                        .userId(userId)
                        .goal(goal)
                        .planDescription(planDesc)
                        .status("ACTIVE")
                        .build();
                path = pathRepo.save(path);

                final Long pathId = path.getId();
                List<LearningPathStep> steps = new ArrayList<>();
                for (int i = 0; i < nodes.size(); i++) {
                    LearningPathDto.PathNode node = nodes.get(i);
                    steps.add(LearningPathStep.builder()
                            .pathId(pathId)
                            .kpId(node.getKpId())
                            .stepOrder(node.getOrder())
                            .completed(false)
                            .status("PENDING")
                            .build());
                }
                stepRepo.saveAll(steps);

                log.info("[LearningPath] 动态路径已保存: id={}, steps={}", pathId, steps.size());

                return LearningPathDto.builder()
                        .pathId(pathId)
                        .goal(goal)
                        .nodes(nodes)
                        .planDescription(planDesc)
                        .build();
            }
        } catch (Exception e) {
            // 检查是否是中断导致的（超时场景）
            if (Thread.currentThread().isInterrupted() || e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                log.warn("[LearningPath] 动态生成被中断（可能是超时）: {}", e.getMessage());
                throw new RuntimeException("学习路径生成超时", e);
            }
            log.error("[LearningPath] 动态生成失败: {}", e.getMessage(), e);
        }

        // 如果动态生成也失败，返回一个基础路径
        List<LearningPathDto.PathNode> fallbackNodes = List.of(
            LearningPathDto.PathNode.builder()
                .kpId("dynamic_fallback_1")
                .title("了解" + goal)
                .description("初步了解" + goal + "的基本概念和应用场景")
                .order(0)
                .completed(false)
                .status("PENDING")
                .build(),
            LearningPathDto.PathNode.builder()
                .kpId("dynamic_fallback_2")
                .title("学习" + goal + "基础")
                .description("系统学习" + goal + "的基础知识和核心概念")
                .order(1)
                .completed(false)
                .status("PENDING")
                .build(),
            LearningPathDto.PathNode.builder()
                .kpId("dynamic_fallback_3")
                .title("实践" + goal)
                .description("通过实践项目巩固所学知识")
                .order(2)
                .completed(false)
                .status("PENDING")
                .build()
        );

        LearningPath path = LearningPath.builder()
                .userId(userId)
                .goal(goal)
                .planDescription("基础学习路径")
                .status("ACTIVE")
                .build();
        path = pathRepo.save(path);

        return LearningPathDto.builder()
                .pathId(path.getId())
                .goal(goal)
                .nodes(fallbackNodes)
                .planDescription("基础学习路径")
                .build();
    }

}