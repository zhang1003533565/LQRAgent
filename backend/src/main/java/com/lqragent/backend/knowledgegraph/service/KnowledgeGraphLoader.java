package com.lqragent.backend.knowledgegraph.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.knowledgegraph.entity.KnowledgeEdge;
import com.lqragent.backend.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.knowledgegraph.repository.KnowledgeEdgeRepository;
import com.lqragent.backend.knowledgegraph.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时读取 knowledge_graph.json，导入 knowledge_point + knowledge_edge 表。
 * 幂等：已存在的 kp_id 不重复插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGraphLoader {

    private static final String GRAPH_JSON = "course/knowledge_graph.json";

    private final KnowledgePointRepository pointRepo;
    private final KnowledgeEdgeRepository edgeRepo;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        if (pointRepo.count() > 0) {
            log.info("[KnowledgeGraph] 图谱已存在 ({} 条)，跳过导入", pointRepo.count());
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(
                    new ClassPathResource(GRAPH_JSON).getInputStream());
            JsonNode chapters = root.get("chapters");

            List<KnowledgePoint> points = new ArrayList<>();
            List<KnowledgeEdge> edges = new ArrayList<>();

            for (JsonNode ch : chapters) {
                String chapterId = ch.get("chapter_id").asText();
                String chapterTitle = ch.get("title").asText();

                for (JsonNode kp : ch.get("knowledge_points")) {
                    String kpId = kp.get("kp_id").asText();
                    String name = kp.get("name").asText();
                    int difficulty = kp.get("difficulty").asInt();

                    points.add(KnowledgePoint.builder()
                            .kpId(kpId)
                            .title(name)
                            .description(chapterTitle + " — " + name)
                            .chapter(chapterId)
                            .difficulty(difficulty)
                            .build());

                    // prerequisites → edges
                    JsonNode prereqs = kp.get("prerequisites");
                    if (prereqs != null && prereqs.isArray()) {
                        for (JsonNode pre : prereqs) {
                            edges.add(KnowledgeEdge.builder()
                                    .fromKpId(pre.asText())
                                    .toKpId(kpId)
                                    .relationType("PREREQUISITE")
                                    .build());
                        }
                    }
                }
            }

            pointRepo.saveAll(points);
            log.info("[KnowledgeGraph] 导入 {} 个知识点", points.size());

            if (!edges.isEmpty()) {
                edgeRepo.saveAll(edges);
                log.info("[KnowledgeGraph] 导入 {} 条依赖关系", edges.size());
            }

        } catch (Exception e) {
            log.error("[KnowledgeGraph] 导入失败: {}", e.getMessage(), e);
        }
    }
}
