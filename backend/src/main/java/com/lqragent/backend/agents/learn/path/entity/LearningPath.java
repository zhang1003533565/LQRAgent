package com.lqragent.backend.agents.learn.path.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_path")
@Comment("学习路径表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("学生用户ID")
    private Long userId;

    @Column(nullable = false, length = 256)
    @Comment("学习目标")
    private String goal;

    @Column(name = "plan_description", columnDefinition = "TEXT")
    @Comment("大模型生成的自然语言学习计划")
    private String planDescription;

    @Column(length = 32)
    @Comment("路径状态：ACTIVE/COMPLETED/ABANDONED")
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
