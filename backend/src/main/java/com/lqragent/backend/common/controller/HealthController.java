package com.lqragent.backend.common.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "健康检查", description = "服务存活探测")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
