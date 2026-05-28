package com.lqragent.backend.quiz.controller;

import com.lqragent.backend.agent.RequestContext;
import com.lqragent.backend.quiz.service.BehaviorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "学习行为", description = "前端埋点上报：点击、停留、查看资源等")
@RestController
@RequestMapping("/api/behavior")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    @Operation(summary = "上报学习行为", description = "前端埋点调用，记录学生行为")
    @PostMapping
    public ResponseEntity<Map<String, Object>> report(
            @RequestBody ReportRequest req) {
        Long userId = RequestContext.getUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "未认证"));

        behaviorService.record(userId, req.kpId(), req.action(), req.durationSec(), req.extra());
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record ReportRequest(String kpId, String action, Integer durationSec, String extra) {}
}
