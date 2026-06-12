package com.lqragent.backend.shared.knowledgegraph.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgeEdge;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgeEdgeRepository;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgePointRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "知识点", description = "知识点查询接口")
@RestController
@RequestMapping("/api/knowledge-points")
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgeEdgeRepository knowledgeEdgeRepository;

    @Operation(summary = "按 kpId 批量查询知识点")
    @GetMapping
    public ApiResponse<List<KnowledgePointDto>> listByKpIds(
            @Parameter(description = "知识点 ID 列表，可重复传参 ids=a&ids=b")
            @RequestParam(name = "ids", required = false) List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.ok(List.of());
        }

        Map<String, Integer> order = new LinkedHashMap<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                order.putIfAbsent(id.trim(), order.size());
            }
        }
        if (order.isEmpty()) {
            return ApiResponse.ok(List.of());
        }

        List<KnowledgePointDto> result = knowledgePointRepository.findByKpIdIn(List.copyOf(order.keySet())).stream()
                .sorted(java.util.Comparator.comparingInt(kp -> order.getOrDefault(kp.getKpId(), Integer.MAX_VALUE)))
                .map(KnowledgePointController::toDto)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    private static KnowledgePointDto toDto(KnowledgePoint kp) {
        return new KnowledgePointDto(kp.getKpId(), kp.getTitle(), kp.getSubject(), kp.getDescription());
    }

    public record KnowledgePointDto(String kpId, String title, String subject, String description) {}

    @Operation(summary = "按 kpId 查询单个知识点详情")
    @GetMapping("/{kpId}")
    public ApiResponse<KnowledgePointDto> getByKpId(
            @PathVariable String kpId) {
        return knowledgePointRepository.findByKpId(kpId)
                .map(kp -> ApiResponse.ok(toDto(kp)))
                .orElse(ApiResponse.fail(404, "知识点不存在: " + kpId));
    }

    @Operation(summary = "按关键词搜索知识点")
    @GetMapping("/search")
    public ApiResponse<List<KnowledgePointDto>> search(
            @Parameter(description = "搜索关键词")
            @RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        List<KnowledgePointDto> result = knowledgePointRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword)
                .stream()
                .map(KnowledgePointController::toDto)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取完整知识图谱（节点+边）")
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getGraph(
            @Parameter(description = "按科目筛选（可选）")
            @RequestParam(required = false) String subject) {
        List<KnowledgePoint> points;
        if (subject != null && !subject.isBlank()) {
            points = knowledgePointRepository.findBySubject(subject);
        } else {
            points = knowledgePointRepository.findAll();
        }

        List<String> kpIds = points.stream().map(KnowledgePoint::getKpId).collect(Collectors.toList());
        List<KnowledgeEdge> edges = knowledgeEdgeRepository.findByFromKpIdInOrToKpIdIn(kpIds, kpIds);

        List<Map<String, Object>> nodeDtos = points.stream().map(kp -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kpId", kp.getKpId());
            m.put("title", kp.getTitle());
            m.put("subject", kp.getSubject());
            m.put("chapter", kp.getChapter());
            m.put("description", kp.getDescription());
            m.put("difficulty", kp.getDifficulty());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edgeDtos = edges.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fromKpId", e.getFromKpId());
            m.put("toKpId", e.getToKpId());
            m.put("relationType", e.getRelationType());
            return m;
        }).collect(Collectors.toList());

        List<String> subjects = points.stream()
                .map(KnowledgePoint::getSubject)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodeDtos);
        result.put("edges", edgeDtos);
        result.put("nodeCount", nodeDtos.size());
        result.put("edgeCount", edgeDtos.size());
        result.put("subjects", subjects);

        return ApiResponse.ok(result);
    }
}
