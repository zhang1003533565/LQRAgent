package com.lqragent.backend.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@Comment("系统用户表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    @Comment("登录用户名")
    private String username;

    @Column(nullable = false)
    @Comment("BCrypt加密密码")
    private String password;

    @Column(nullable = false, length = 128)
    @Comment("显示名称")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Comment("角色：STUDENT/TEACHER/ADMIN")
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    @Comment("是否启用")
    private Boolean enabled = true;

    @CreationTimestamp
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    public enum Role {
        STUDENT, TEACHER, ADMIN
    }
}
