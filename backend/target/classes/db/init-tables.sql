-- 应用启动时自动执行（CREATE IF NOT EXISTS，可重复运行）
-- 补全 schema.sql 中定义、但 JPA 尚未映射的全部业务表

-- 1. 用户与系统配置
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`     VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    `password`     VARCHAR(256) NOT NULL COMMENT 'BCrypt加密密码',
    `display_name` VARCHAR(128) NOT NULL COMMENT '显示名称',
    `role`         VARCHAR(16)  NOT NULL COMMENT '角色：STUDENT/TEACHER/ADMIN',
    `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0禁用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key`   VARCHAR(100) NOT NULL COMMENT '配置键，如 llm.api_key',
    `config_value` TEXT         NOT NULL COMMENT '配置值',
    `remark`       VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统动态参数配置表';

-- 2. 学生画像
CREATE TABLE IF NOT EXISTS `learner_profile` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`                 BIGINT       NOT NULL COMMENT '关联用户ID',
    `knowledge_level`         VARCHAR(32)  DEFAULT 'BEGINNER' COMMENT '知识水平：BEGINNER/INTERMEDIATE/ADVANCED',
    `learning_goal`           TEXT         COMMENT '当前学习目标',
    `cognitive_style`         VARCHAR(64)  COMMENT '认知风格：visual/reading/practice',
    `common_errors`           TEXT         COMMENT '常见错误（JSON数组）',
    `learning_pace`           VARCHAR(32)  DEFAULT 'NORMAL' COMMENT '学习节奏：SLOW/NORMAL/FAST',
    `interest_direction`      TEXT         COMMENT '兴趣方向（JSON数组）',
    `preferred_resource_type` VARCHAR(64)  COMMENT '偏好资源类型：video/text/code',
    `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像表';

-- 3. 知识图谱
CREATE TABLE IF NOT EXISTS `knowledge_point` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `kp_id`       VARCHAR(64)  NOT NULL COMMENT '知识点唯一标识，如 kp_decorator',
    `title`       VARCHAR(128) NOT NULL COMMENT '知识点名称',
    `description` TEXT         COMMENT '知识点描述',
    `chapter`     VARCHAR(64)  COMMENT '所属章节',
    `difficulty`  TINYINT      DEFAULT 1 COMMENT '难度等级：1-5',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kp_id` (`kp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

CREATE TABLE IF NOT EXISTS `knowledge_edge` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `from_kp_id`    VARCHAR(64) NOT NULL COMMENT '前置知识点ID',
    `to_kp_id`      VARCHAR(64) NOT NULL COMMENT '后置知识点ID',
    `relation_type` VARCHAR(32) DEFAULT 'PREREQUISITE' COMMENT '关系类型：PREREQUISITE等',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edge` (`from_kp_id`, `to_kp_id`),
    CONSTRAINT `fk_edge_from` FOREIGN KEY (`from_kp_id`) REFERENCES `knowledge_point` (`kp_id`),
    CONSTRAINT `fk_edge_to`   FOREIGN KEY (`to_kp_id`)   REFERENCES `knowledge_point` (`kp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点依赖关系表';

-- 4. 学习路径
CREATE TABLE IF NOT EXISTS `learning_path` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`          BIGINT       NOT NULL COMMENT '学生用户ID',
    `goal`             VARCHAR(256) NOT NULL COMMENT '学习目标描述',
    `plan_description` TEXT         COMMENT '大模型生成的自然语言学习计划',
    `status`           VARCHAR(32)  DEFAULT 'ACTIVE' COMMENT '路径状态：ACTIVE/COMPLETED/ABANDONED',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_path_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习路径表';

CREATE TABLE IF NOT EXISTS `learning_path_step` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `path_id`      BIGINT      NOT NULL COMMENT '所属学习路径ID',
    `kp_id`        VARCHAR(64) NOT NULL COMMENT '知识点ID',
    `step_order`   INT         NOT NULL COMMENT '步骤顺序号',
    `completed`    TINYINT(1)  DEFAULT 0 COMMENT '是否完成：1是 0否',
    `status`       VARCHAR(32) DEFAULT 'PENDING' COMMENT '步骤状态：PENDING/ACTIVE/COMPLETED/SKIPPED',
    `completed_at` DATETIME    COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_path_id` (`path_id`),
    CONSTRAINT `fk_step_path` FOREIGN KEY (`path_id`) REFERENCES `learning_path` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习路径步骤表';

-- 5. 资源
CREATE TABLE IF NOT EXISTS `resource_item` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `kp_id`         VARCHAR(64)  NOT NULL COMMENT '关联知识点ID',
    `resource_type` VARCHAR(32)  NOT NULL COMMENT '资源类型：LESSON/QUIZ/CODE_CASE/MIND_MAP/SUMMARY',
    `title`         VARCHAR(256) COMMENT '资源标题',
    `content`       LONGTEXT     COMMENT '资源内容（Markdown或JSON）',
    `quality_score` TINYINT      COMMENT '质量评分：0-100',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_kp_id` (`kp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习资源内容表';

-- 6. 答题记录
CREATE TABLE IF NOT EXISTS `quiz_record` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT      NOT NULL COMMENT '学生用户ID',
    `kp_id`       VARCHAR(64) NOT NULL COMMENT '知识点ID',
    `resource_id` BIGINT      COMMENT '关联资源ID',
    `score`       TINYINT     COMMENT '得分：0-100',
    `is_correct`  TINYINT(1)  COMMENT '是否正确：1是 0否',
    `answer`      TEXT        COMMENT '学生提交的答案',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_kp` (`user_id`, `kp_id`),
    CONSTRAINT `fk_quiz_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录表';

-- 7. 学习行为日志
CREATE TABLE IF NOT EXISTS `study_behavior` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT      NOT NULL COMMENT '学生用户ID',
    `kp_id`        VARCHAR(64) COMMENT '关联知识点ID',
    `action`       VARCHAR(64) NOT NULL COMMENT '行为类型：VIEW/CHAT/QUIZ/UPLOAD',
    `duration_sec` INT         COMMENT '停留时长（秒）',
    `extra`        TEXT        COMMENT '附加信息（JSON）',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '行为发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习行为日志表';

-- 8. 路径调整日志
CREATE TABLE IF NOT EXISTS `path_adjustment_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        BIGINT       NOT NULL COMMENT '学生用户ID',
    `path_id`        BIGINT       NOT NULL COMMENT '学习路径ID',
    `trigger_reason` VARCHAR(128) COMMENT '触发原因：LOW_SCORE/REPEAT_ERROR/INTEREST',
    `action`         VARCHAR(128) COMMENT '调整动作：INSERT_REVIEW/ADD_CODE_CASE/ADD_READING',
    `kp_id`          VARCHAR(64)  COMMENT '被插入或调整的知识点ID',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_path` (`user_id`, `path_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路径动态调整日志表';

-- 9. 上传任务队列
CREATE TABLE IF NOT EXISTS `kb_upload_task` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`       BIGINT        NOT NULL COMMENT '上传用户ID',
    `file_name`     VARCHAR(256)  NOT NULL COMMENT '原始文件名',
    `file_path`     VARCHAR(512)  NOT NULL COMMENT '临时存储路径或对象存储Key',
    `kb_scope`      VARCHAR(16)   NOT NULL DEFAULT 'PERSONAL' COMMENT '知识库范围：PUBLIC公开/PERSONAL个人',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `priority`      INT           NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先',
    `error_message` VARCHAR(1024) COMMENT '失败原因说明',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    `started_at`    DATETIME      COMMENT '开始处理时间',
    `finished_at`   DATETIME      COMMENT '处理完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_priority` (`status`, `priority`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库上传任务队列表';

-- 10. 智能体对话历史
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT       NOT NULL COMMENT '学生用户ID',
    `session_id`   VARCHAR(64)  NOT NULL COMMENT '会话隔离ID',
    `sender`       VARCHAR(16)  NOT NULL COMMENT '发送方：USER用户/AI智能体',
    `content_type` VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT '内容类型：TEXT纯文本/MULTI_CARD多模态卡片',
    `body`         LONGTEXT     NOT NULL COMMENT '消息主体（文本或多模态卡片JSON）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体交互对话历史表';
