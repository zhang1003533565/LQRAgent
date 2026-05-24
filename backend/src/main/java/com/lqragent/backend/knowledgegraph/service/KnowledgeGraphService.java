package com.lqragent.backend.knowledgegraph.service;

import com.lqragent.backend.knowledgegraph.entity.KnowledgeEdge;
import com.lqragent.backend.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.knowledgegraph.repository.KnowledgeEdgeRepository;
import com.lqragent.backend.knowledgegraph.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱查询服务。
 * 提供知识点查找、前置/后置查询、最短路径规划。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final KnowledgePointRepository pointRepo;
    private final KnowledgeEdgeRepository edgeRepo;

    /** 查单个知识点 */
    public Optional<KnowledgePoint> getByKpId(String kpId) {
        return pointRepo.findByKpId(kpId);
    }

    /** 所有知识点（按章节排序） */
    public List<KnowledgePoint> getAll() {
        return pointRepo.findAll();
    }

    /** 某知识点的所有前置知识点 */
    public List<KnowledgePoint> getPrerequisites(String kpId) {
        return edgeRepo.findByToKpId(kpId).stream()
                .map(e -> pointRepo.findByKpId(e.getFromKpId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 以某知识点为前置的所有后置知识点 */
    public List<KnowledgePoint> getDependents(String kpId) {
        return edgeRepo.findByFromKpId(kpId).stream()
                .map(e -> pointRepo.findByKpId(e.getToKpId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 计算从 currentKpId 到 targetKpId 的最短学习路径（BFS）。
     * 只走 PREREQUISITE 方向（前置→后置，即学完 current 才能学 target）。
     * 若无法到达返回空列表。
     */
    public List<String> findShortestPath(String currentKpId, String targetKpId) {
        if (currentKpId.equals(targetKpId)) {
            return List.of(currentKpId);
        }

        // 邻接表：from → [to, ...]
        Map<String, List<String>> graph = buildAdjacencyList();

        // BFS
        Queue<String> queue = new LinkedList<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.offer(currentKpId);
        visited.add(currentKpId);

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : graph.getOrDefault(cur, List.of())) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    prev.put(next, cur);
                    queue.offer(next);
                    if (next.equals(targetKpId)) {
                        return reconstructPath(prev, currentKpId, targetKpId);
                    }
                }
            }
        }

        log.warn("[KnowledgeGraph] 无法找到从 {} 到 {} 的路径", currentKpId, targetKpId);
        return List.of();
    }

    /**
     * 替代 findShortestPath：用于不知道目标时，
     * 从 currentKpId 往后推荐一条完整路径直到没有后置。
     */
    public List<String> findForwardPath(String startKpId) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cur = startKpId;

        while (cur != null && !visited.contains(cur)) {
            path.add(cur);
            visited.add(cur);
            List<KnowledgePoint> deps = getDependents(cur);
            cur = deps.isEmpty() ? null : deps.get(0).getKpId();
        }
        return path;
    }

    // ===== internal =====

    private Map<String, List<String>> buildAdjacencyList() {
        List<KnowledgeEdge> allEdges = edgeRepo.findAll();
        Map<String, List<String>> graph = new HashMap<>();
        for (KnowledgeEdge e : allEdges) {
            graph.computeIfAbsent(e.getFromKpId(), k -> new ArrayList<>()).add(e.getToKpId());
        }
        return graph;
    }

    private List<String> reconstructPath(Map<String, String> prev, String start, String end) {
        LinkedList<String> path = new LinkedList<>();
        String cur = end;
        while (cur != null) {
            path.addFirst(cur);
            cur = prev.get(cur);
        }
        return path.getFirst().equals(start) ? path : List.of();
    }
}
