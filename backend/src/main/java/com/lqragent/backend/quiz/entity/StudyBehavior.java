package com.lqragent.backend.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_behavior")
@Comment("学习行为记录表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyBehavior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("学生ID")
    private Long userId;

    @Column(name = "kp_id", length = 64)
    @Comment("关联知识点")
    private String kpId;

    @Column(name = "action", length = 64, nullable = false)
    @Comment("行为类型：VIEW/CHAT/QUIZ/UPLOAD")
    private String action;

    @Column(name = "duration_sec")
    @Comment("停留秒数")
    private Integer durationSec;

    @Column(name = "extra", columnDefinition = "TEXT")
    @Comment("附加JSON")
    private String extra;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("行为时间")
    private LocalDateTime createdAt;

    @PrePersist
    void init() {
        createdAt = LocalDateTime.now();
    }
}
