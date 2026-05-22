package com.lqragent.backend.systemconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_config")
@Comment("系统动态参数配置表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    @Comment("配置键")
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    @Comment("配置值")
    private String configValue;

    @Column(length = 255)
    @Comment("备注说明")
    private String remark;

    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
}
