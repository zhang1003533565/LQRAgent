package com.lqragent.backend.prompt.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 提示词实体
 * 支持动态修改提示词，无需重启服务
 */
@Entity
@Table(name = "agent_prompt", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id"})
})
@Comment("Agent 提示词配置表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 64)
    @Comment("Agent 唯一标识，如 qa_agent、profile_agent")
    private String agentId;

    @Column(name = "agent_name", length = 128)
    @Comment("Agent 显示名称")
    private String agentName;

    @Lob
    @Column(name = "prompt_content", nullable = false, columnDefinition = "LONGTEXT")
    @Comment("提示词内容（Markdown 格式）")
    private String promptContent;

    @Lob
    @Column(name = "default_content", columnDefinition = "LONGTEXT")
    @Comment("默认提示词（从文件加载，用于重置）")
    private String defaultContent;

    @Column(name = "version", nullable = false)
    @Comment("版本号，每次修改递增")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "updated_by", length = 64)
    @Comment("最后修改人")
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("更新时间")
    private LocalDateTime updatedAt;
}
