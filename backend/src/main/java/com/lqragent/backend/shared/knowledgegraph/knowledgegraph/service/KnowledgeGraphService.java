package com.lqragent.backend.agents.knowledgegraph.service;

import com.lqragent.backend.agents.knowledgegraph.entity.KnowledgeEdge;
import com.lqragent.backend.agents.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.agents.knowledgegraph.repository.KnowledgeEdgeRepository;
import com.lqragent.backend.agents.knowledgegraph.repository.KnowledgePointRepository;
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

    /**
     * DAG 拓扑排序（Kahn 算法）— 返回全局拓扑序。
     * <p>
     * 比 BFS 更符合教学逻辑：保证每个节点出现在所有前置之后。
     * LLM 的职责从"排顺序"变为"调节点权重、过滤已掌握节点"。
     * </p>
     */
    public List<String> topologicalSort() {
        Map<String, List<String>> graph = buildAdjacencyList();
        // 入度表
        Map<String, Integer> inDegree = new HashMap<>();
        for (KnowledgePoint kp : getAll()) {
            inDegree.putIfAbsent(kp.getKpId(), 0);
        }
        for (List<String> neighbors : graph.values()) {
            for (String n : neighbors) {
                inDegree.merge(n, 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.offer(e.getKey());
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            result.add(cur);
            for (String next : graph.getOrDefault(cur, List.of())) {
                int deg = inDegree.merge(next, -1, Integer::sum);
                if (deg == 0) queue.offer(next);
            }
        }

        if (result.size() < getAll().size()) {
            log.warn("[KnowledgeGraph] 拓扑排序未覆盖所有节点：{}/{}",
                    result.size(), getAll().size());
        }
        return result;
    }

    /**
     * 从拓扑排序中取目标节点之前（含）的片段。
     * 适合"学完 A 到目标 T 的合理学习路径"。
     */
    public List<String> topologicalPathTo(String targetKpId) {
        List<String> topo = topologicalSort();
        int idx = topo.indexOf(targetKpId);
        if (idx < 0) {
            log.warn("[KnowledgeGraph] 目标 {} 不在拓扑序中", targetKpId);
            return topo; // 返回全部
        }
        return topo.subList(0, idx + 1);
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
