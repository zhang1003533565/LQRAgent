package com.lqragent.backend.agents.check.contentassessment.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感内容过滤器 — 本地敏感词库 + 正则匹配。
 * 词库从 resources/sensitive-words.txt 加载（每行一个，支持 # 注释）。
 */
@Slf4j
@Service
public class SensitiveFilter {

    private final List<String> sensitiveWords = new ArrayList<>();

    @PostConstruct
    void load() {
        try (var is = getClass().getResourceAsStream("/sensitive-words.txt");
             var reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    sensitiveWords.add(line);
                }
            }
            log.info("[SensitiveFilter] 已加载 {} 条敏感词", sensitiveWords.size());
        } catch (Exception e) {
            log.warn("[SensitiveFilter] 敏感词库未找到或加载失败: {}", e.getMessage());
        }
    }

    /**
     * 检查内容是否包含敏感词。
     * @return 如果包含敏感词返回 fail + 原因，否则 pass
     */
    public CheckResult check(String content) {
        if (content == null || content.isBlank()) return CheckResult.pass();
        String lower = content.toLowerCase();
        for (String word : sensitiveWords) {
            if (lower.contains(word)) {
                return CheckResult.fail("包含敏感词: " + word);
            }
        }
        return CheckResult.pass();
    }

    public record CheckResult(boolean passed, String reason) {
        public static CheckResult pass() { return new CheckResult(true, ""); }
        public static CheckResult fail(String reason) { return new CheckResult(false, reason); }
    }
}
