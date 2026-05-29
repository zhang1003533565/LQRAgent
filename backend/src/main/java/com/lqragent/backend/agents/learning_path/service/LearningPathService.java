package com.lqragent.backend.agents.learning_path.service;

import com.lqragent.backend.agents.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.agents.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.framework.llm.LlmContentGenerator;
import com.lqragent.backend.agents.learner_profile.dto.ProfileSummaryDto;
import com.lqragent.backend.agents.learner_profile.service.LearnerProfileService;
import com.lqragent.backend.agents.learning_path.dto.LearningPathDto;
import com.lqragent.backend.agents.learning_path.entity.LearningPath;
import com.lqragent.backend.agents.learning_path.entity.LearningPathStep;
import com.lqragent.backend.agents.learning_path.repository.LearningPathRepository;
import com.lqragent.backend.agents.learning_path.repository.LearningPathStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final LlmContentGenerator llmGenerator;
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
        // 1. 解析目标 → 找到知识点ID
        String targetKpId = resolveKpId(goal);
        log.info("[LearningPath] generatePath: userId={}, goal={}, target={}, current={}",
                userId, goal, targetKpId, currentKpId);

        // 2. 确定起始点
        String fromKpId = currentKpId;
        if (fromKpId == null || fromKpId.isBlank()) {
            fromKpId = findFirstKpId();
        }

        // 3. DAG 拓扑排序（保证前置知识在前的正确顺序）
        //    比 BFS 更符合教学逻辑。LLM 的职责从"排顺序"变为"调节点权重"。
        List<String> pathKpIds = kgService.topologicalPathTo(targetKpId);
        if (pathKpIds.isEmpty()) {
            log.warn("[LearningPath] 拓扑排序无法找到 {}，使用 BFS 兜底", targetKpId);
            pathKpIds = kgService.findShortestPath(fromKpId, targetKpId);
            if (pathKpIds.isEmpty()) {
                pathKpIds = kgService.findForwardPath(fromKpId);
                if (!pathKpIds.contains(targetKpId)) {
                    pathKpIds.add(targetKpId);
                }
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
        // 否则在所有知识点标题中模糊匹配
        List<KnowledgePoint> all = kgService.getAll();
        // 优先精确匹配
        for (KnowledgePoint kp : all) {
            if (kp.getTitle().contains(goal) || goal.contains(kp.getTitle())) {
                return kp.getKpId();
            }
        }
        // 最后按关键词匹配
        for (KnowledgePoint kp : all) {
            if (kp.getDescription() != null && kp.getDescription().contains(goal)) {
                return kp.getKpId();
            }
        }
        // 无匹配时返回第一个知识点作为默认目标
        if (!all.isEmpty()) {
            log.warn("[LearningPath] 未匹配到知识点, goal={}, 使用第一个KP: {}", goal, all.get(0).getKpId());
            return all.get(0).getKpId();
        }
        throw new IllegalArgumentException("知识图谱为空，无法规划学习路径");
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
            List<String> sorted = llmGenerator.generatePathSort(kpIds, profileHint);

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
}
