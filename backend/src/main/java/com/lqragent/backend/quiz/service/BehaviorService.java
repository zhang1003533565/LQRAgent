package com.lqragent.backend.quiz.service;

import com.lqragent.backend.quiz.entity.StudyBehavior;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学习行为追踪服务。
 * 记录前端埋点上报的行为（点击、停留、查看资源等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorService {

    private final StudyBehaviorRepository repository;

    /**
     * 记录一条学习行为。
     */
    public StudyBehavior record(Long userId, String kpId, String action, Integer durationSec, String extra) {
        StudyBehavior b = StudyBehavior.builder()
                .userId(userId)
                .kpId(kpId)
                .action(action)
                .durationSec(durationSec)
                .extra(extra)
                .build();
        b = repository.save(b);
        log.debug("[Behavior] record: userId={}, kpId={}, action={}, duration={}s", userId, kpId, action, durationSec);
        return b;
    }

    /**
     * 查询用户的所有行为记录。
     */
    public List<StudyBehavior> getByUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
