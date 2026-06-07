package com.lqragent.backend.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_record")
@Comment("答题记录表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("学生ID")
    private Long userId;

    @Column(name = "question_id")
    @Comment("题目ID")
    private Long questionId;

    @Column(name = "kp_id", length = 64, nullable = false)
    @Comment("知识点ID")
    private String kpId;

    @Column(name = "resource_id")
    @Comment("关联资源ID")
    private Long resourceId;

    @Column(name = "score", columnDefinition = "TINYINT")
    @Comment("得分 0-100")
    private Integer score;

    @Column(name = "is_correct", columnDefinition = "TINYINT(1)")
    @Comment("1=正确 0=错误")
    private Boolean isCorrect;

    @Column(name = "answer", columnDefinition = "TEXT")
    @Comment("学生提交答案")
    private String answer;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("答题时间")
    private LocalDateTime createdAt;

    @PrePersist
    void init() {
        createdAt = LocalDateTime.now();
    }
}
