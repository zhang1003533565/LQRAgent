package com.lqragent.backend.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_practice_session")
@Comment("答题练习会话表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizPracticeSession {

    @Id
    @Column(length = 64)
    @Comment("会话ID")
    private String id;

    @Column(name = "user_id", nullable = false)
    @Comment("学生ID")
    private Long userId;

    @Column(name = "session_data", columnDefinition = "LONGTEXT", nullable = false)
    @Comment("会话 JSON 快照")
    private String sessionData;

    @Column(length = 32)
    @Comment("会话状态")
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
