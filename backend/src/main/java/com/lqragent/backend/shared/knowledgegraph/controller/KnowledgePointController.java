package com.lqragent.backend.shared.knowledgegraph.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgePointRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "知识点", description = "知识点查询接口")
@RestController
@RequestMapping("/api/knowledge-points")
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointRepository knowledgePointRepository;

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
}
