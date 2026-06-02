package com.lqragent.backend.agents.learner_profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "learner_profile")
@Comment("学生画像表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    @Comment("关联用户ID")
    private Long userId;

    @Column(name = "knowledge_level", length = 32)
    @Comment("知识水平：BEGINNER/INTERMEDIATE/ADVANCED")
    @Builder.Default
    private String knowledgeLevel = "BEGINNER";

    @Column(name = "learning_goal", columnDefinition = "TEXT")
    @Comment("当前学习目标")
    private String learningGoal;

    @Column(name = "cognitive_style", length = 64)
    @Comment("认知风格：visual/reading/practice")
    private String cognitiveStyle;

    @Column(name = "common_errors", columnDefinition = "TEXT")
    @Comment("常见错误（JSON数组）")
    private String commonErrors;

    @Column(name = "learning_pace", length = 32)
    @Comment("学习节奏：SLOW/NORMAL/FAST")
    @Builder.Default
    private String learningPace = "NORMAL";

    @Column(name = "interest_direction", columnDefinition = "TEXT")
    @Comment("兴趣方向（JSON数组）")
    private String interestDirection;

    @Column(name = "preferred_resource_type", length = 64)
    @Comment("偏好资源类型：video/text/code")
    private String preferredResourceType;

    @Column(name = "topic_mastery", columnDefinition = "TEXT")
    @Comment("知识点掌握状态 JSON，如 {\"for循环\":\"MASTERED\",\"装饰器\":\"PENDING\"}")
    private String topicMastery;

    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
}
