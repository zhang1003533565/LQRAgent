package com.lqragent.backend.core.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期清理过期 SessionContext，防止静态 Map 内存泄漏。
 */
@Slf4j
@Component
public class SessionContextCleanupTask {

    @Scheduled(fixedRate = 300_000)
    public void evictExpiredSessions() {
        int removed = SessionContext.evictExpired();
        if (removed > 0) {
            log.info("[SessionContext] 清理过期会话 {} 个，当前活跃 {}", removed, SessionContext.activeCount());
        }
    }
}
