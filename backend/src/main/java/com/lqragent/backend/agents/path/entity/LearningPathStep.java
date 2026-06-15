package com.lqragent.backend.agents.path.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_path_step")
@Comment("学习路径步骤表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPathStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "path_id", nullable = false)
    @Comment("所属学习路径ID")
    private Long pathId;

    @Column(name = "kp_id", nullable = false, length = 64)
    @Comment("知识点ID")
    private String kpId;

    @Column(name = "step_order", nullable = false)
    @Comment("步骤顺序号")
    private Integer stepOrder;

    @Column(nullable = false)
    @Comment("是否完成：1是 0否")
    @Builder.Default
    private Boolean completed = false;

    @Column(length = 32)
    @Comment("步骤状态：PENDING/ACTIVE/COMPLETED/SKIPPED")
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "completed_at")
    @Comment("完成时间")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (completed != null && completed) {
            completedAt = LocalDateTime.now();
        }
    }
}
