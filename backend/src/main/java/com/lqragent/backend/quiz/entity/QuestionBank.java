package com.lqragent.backend.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_bank")
@Comment("题库表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("题目ID")
    private Long id;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    @Comment("题目内容（题干）")
    private String title;

    @Column(name = "code_content", columnDefinition = "TEXT")
    @Comment("Python代码片段")
    private String codeContent;

    @Column(name = "question_type", nullable = false, length = 30)
    @Comment("题型")
    private String questionType;

    @Column(name = "option_a", length = 500)
    @Comment("选项A")
    private String optionA;

    @Column(name = "option_b", length = 500)
    @Comment("选项B")
    private String optionB;

    @Column(name = "option_c", length = 500)
    @Comment("选项C")
    private String optionC;

    @Column(name = "option_d", length = 500)
    @Comment("选项D")
    private String optionD;

    @Column(name = "correct_answer", nullable = false, columnDefinition = "TEXT")
    @Comment("正确答案")
    private String correctAnswer;

    @Column(name = "analysis", columnDefinition = "TEXT")
    @Comment("题目解析")
    private String analysis;

    @Column(name = "difficulty", nullable = false, columnDefinition = "TINYINT")
    @Comment("难度")
    private Integer difficulty;

    @Column(name = "knowledge_point", length = 100)
    @Comment("所属知识点")
    private String knowledgePoint;

    @Column(name = "status", nullable = false, columnDefinition = "TINYINT")
    @Comment("状态")
    private Integer status;

    @Column(name = "create_time", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updateTime;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
    }

    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
