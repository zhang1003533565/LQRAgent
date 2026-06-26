-- 应用启动时自动执行（可重复运行）
-- JPA 只为 @Entity 建表；其余业务表由此脚本补全
-- 顺序：先处理遗留表名 → 全量建表 → 旧库缺列补丁（须在 CREATE 之后）

-- ========== 遗留表名（须在 CREATE sys_user 之前）==========

-- 规避 MySQL 保留字：user → sys_user
SET @rename_user := (
    SELECT COUNT(*) = 0 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_user'
) AND (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'user'
);
SET @sql_rename_user := IF(
    @rename_user,
    'RENAME TABLE `user` TO `sys_user`',
    'SELECT ''skip: sys_user already exists or user missing'''
);
PREPARE stmt_rename_user FROM @sql_rename_user;
EXECUTE stmt_rename_user;
DEALLOCATE PREPARE stmt_rename_user;

-- ========== 全量建表（CREATE IF NOT EXISTS）==========

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
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `kp_id`              VARCHAR(64)  NOT NULL COMMENT '关联知识点ID',
    `resource_type`      VARCHAR(32)  NOT NULL COMMENT '资源类型：LESSON/QUIZ/CODE_CASE/MIND_MAP/SUMMARY/ILLUSTRATION/VIDEO_CLIP',
    `title`              VARCHAR(256) COMMENT '资源标题',
    `content`            LONGTEXT     COMMENT '文本资源内容（Markdown或JSON）；媒体类可存说明文案',
    `media_url`          VARCHAR(1024) COMMENT '媒体资源地址（生图/生视频结果）',
    `media_mime`         VARCHAR(64)  COMMENT '媒体 MIME：image/png、video/mp4 等',
    `generation_prompt`  TEXT         COMMENT '生图/生视频所用提示词',
    `quality_score`      TINYINT      COMMENT '质量评分：0-100',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_kp_id` (`kp_id`),
    KEY `idx_resource_type` (`resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习资源内容表';

-- 6. 答题记录
CREATE TABLE IF NOT EXISTS `quiz_record` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT      NOT NULL COMMENT '学生用户ID',
    `question_id` BIGINT      DEFAULT NULL COMMENT '题目ID',
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

-- 7. 题库
CREATE TABLE IF NOT EXISTS `question_bank` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `title`           TEXT         NOT NULL COMMENT '题目内容（题干）',
    `code_content`    TEXT         DEFAULT NULL COMMENT 'Python代码片段',
    `question_type`   VARCHAR(30)  NOT NULL COMMENT '题型（single单选、judge判断、fill填空、code_reading代码阅读）',
    `option_a`        VARCHAR(500) DEFAULT NULL COMMENT '选项A',
    `option_b`        VARCHAR(500) DEFAULT NULL COMMENT '选项B',
    `option_c`        VARCHAR(500) DEFAULT NULL COMMENT '选项C',
    `option_d`        VARCHAR(500) DEFAULT NULL COMMENT '选项D',
    `correct_answer`  TEXT         NOT NULL COMMENT '正确答案',
    `analysis`        TEXT         DEFAULT NULL COMMENT '题目解析',
    `difficulty`      TINYINT      NOT NULL DEFAULT 1 COMMENT '难度（1简单、2中等、3困难）',
    `knowledge_point` VARCHAR(100) DEFAULT NULL COMMENT '所属知识点',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1启用，0禁用）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_question_type` (`question_type`),
    KEY `idx_difficulty` (`difficulty`),
    KEY `idx_knowledge_point` (`knowledge_point`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库表';

-- 7.1 题库种子数据（幂等：IGNORE 跳过已存在的记录）
INSERT IGNORE INTO `question_bank` (`id`, `title`, `question_type`, `option_a`, `option_b`, `option_c`, `option_d`, `correct_answer`, `analysis`, `difficulty`, `knowledge_point`) VALUES
(1,  'Python 中以下哪个函数用于从用户获取输入？', 'single', 'print()', 'input()', 'format()', 'len()', 'B', 'input() 是 Python 内置函数，用于接收用户键盘输入，返回字符串类型。', 1, 'kp_input_output'),
(2,  '以下代码的输出是什么？\nprint(type(3.14))', 'single', '<class ''int''>', '<class ''float''>', '<class ''str''>', '<class ''bool''>', 'B', '3.14 是一个浮点数字面量，type() 返回 <class ''float''>。', 1, 'kp_variables'),
(3,  'Python 中字符串拼接可以使用哪个运算符？', 'single', '&', '&&', '+', '|', 'C', 'Python 中用 + 拼接字符串，如 "Hello " + "World"。& 是位运算，&& 是其他语言的逻辑与。', 1, 'kp_string'),
(4,  '以下代码的输出是什么？\na = 10\nb = 3\nprint(a % b)', 'single', '3', '3.33', '1', '0', 'C', '% 是取模运算符，10 除以 3 余数为 1。', 1, 'kp_operators'),
(5,  '以下关于 Python 列表的描述，哪个是正确的？', 'single', '列表元素类型必须相同', '列表是不可变的', '列表用方括号 [] 定义', '列表没有索引', 'C', '列表用 [] 定义，可以包含不同类型元素，可变，支持索引访问。', 1, 'kp_list'),
(6,  '以下代码的输出是什么？\nx = 5\nif x > 3:\n    print("A")\nelse:\n    print("B")', 'single', 'A', 'B', 'AB', '无输出', 'A', 'x=5>3 条件成立，执行 if 分支输出 "A"。', 2, 'kp_if'),
(7,  '以下代码输出多少个数字？\nfor i in range(3):\n    print(i)', 'single', '2', '3', '4', '0', 'B', 'range(3) 生成 0, 1, 2 三个数，循环执行 3 次。', 2, 'kp_for'),
(8,  '字典中访问值使用什么？', 'single', '索引（数字）', '键（key）', '下标', '指针', 'B', '字典是键值对结构，通过 dict[key] 访问对应的值。', 2, 'kp_dict'),
(9,  'while 循环中，哪个关键字可以立即终止循环？', 'single', 'continue', 'pass', 'break', 'return', 'C', 'break 立即跳出当前循环；continue 跳过本次迭代进入下一次；pass 是空语句。', 2, 'kp_while'),
(10, 'Python 中列表和元组的主要区别是？', 'judge', NULL, NULL, NULL, NULL, '列表可变，元组不可变', '列表用 [] 定义，支持增删改；元组用 () 定义，创建后不能修改。', 2, 'kp_tuple'),
(11, '函数定义使用哪个关键字？', 'fill', NULL, NULL, NULL, NULL, 'def', 'Python 使用 def 关键字定义函数，如 def foo():。', 2, 'kp_list'),
(12, 'print(2 ** 3) 的输出是？', 'fill', NULL, NULL, NULL, NULL, '8', '** 是幂运算，2**3 = 2*2*2 = 8。', 1, 'kp_operators'),
(13, 'break 用于跳过本次循环进入下一次迭代。', 'judge', NULL, NULL, NULL, NULL, '错误', 'break 是终止整个循环，continue 才是跳过本次迭代。', 2, 'kp_break_continue'),
(14, '在 Python 中，集合（set）中的元素可以重复。', 'judge', NULL, NULL, NULL, NULL, '错误', '集合中的元素是唯一的，不允许重复。', 2, 'kp_set'),
(15, '下面代码的输出是什么？\ns = "Hello"\nprint(s[1])', 'single', 'H', 'e', 'l', 'o', 'B', 'Python 字符串索引从 0 开始，s[0]="H"，s[1]="e"。', 2, 'kp_string'),
(16, '以下哪个是正确的注释语法？', 'single', '// 注释', '<!-- 注释 -->', '# 注释', '/* 注释 */', 'C', 'Python 单行注释使用 #，多行注释使用三引号 '''''' 或 """"""。', 1, 'kp_intro'),
(17, 'if 语句中，条件表达式后面的符号是？', 'fill', NULL, NULL, NULL, NULL, ':', 'Python if 语句格式：if 条件:（条件后必须跟冒号）。', 2, 'kp_if'),
(18, '以下代码会输出多少次 Hello？\ni = 0\nwhile i < 2:\n    print("Hello")\n    i += 1', 'single', '0', '1', '2', '3', 'C', 'i 从 0 开始，i=0 打印一次，i=1 打印一次，i=2 时条件不满足退出。', 2, 'kp_while'),
(19, 'range(1, 5) 生成的数字序列是？', 'single', '1,2,3,4,5', '1,2,3,4', '0,1,2,3,4', '1,2,3,4,5,6', 'B', 'range(start, stop) 含 start 不含 stop，即 [1, 5) 区间。', 2, 'kp_for'),
(20, '以下哪个方法可以向列表末尾添加元素？', 'single', 'insert()', 'add()', 'append()', 'push()', 'C', 'list.append(item) 在末尾添加元素；list.insert(idx, item) 在指定位置插入。Python 列表没有 add() 或 push() 方法。', 2, 'kp_list');

-- 8. 学习行为日志
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

-- 9. 路径调整日志
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

-- 10. 上传任务队列
CREATE TABLE IF NOT EXISTS `kb_upload_task` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`       BIGINT        NOT NULL COMMENT '上传用户ID',
    `file_name`     VARCHAR(256)  NOT NULL COMMENT '原始文件名',
    `file_path`     VARCHAR(512)  NOT NULL COMMENT '临时存储路径或对象存储Key',
    `kb_scope`      VARCHAR(16)   NOT NULL DEFAULT 'PERSONAL' COMMENT '知识库范围：PUBLIC公开/PERSONAL个人',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `priority`      INT           NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先',
    `error_message` VARCHAR(1024) COMMENT '失败原因说明',
    `analysis_result` TEXT         COMMENT '内容分析智能体输出（JSON）',
    `mapped_kp_ids`   TEXT         COMMENT '映射到的知识点ID列表（JSON数组）',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    `started_at`    DATETIME      COMMENT '开始处理时间',
    `finished_at`   DATETIME      COMMENT '处理完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_priority` (`status`, `priority`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库上传任务队列表';

-- 11. 智能体对话历史
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

-- 12. 聊天会话
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`                   VARCHAR(64)  NOT NULL COMMENT '会话ID（UUID）',
    `user_id`              BIGINT       NOT NULL COMMENT '学生用户ID',
    `title`                VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    `ai_server_session_id` VARCHAR(128) DEFAULT NULL COMMENT 'ai-server 侧会话ID',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_updated` (`user_id`, `updated_at`),
    CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 12. 智能体运行日志
CREATE TABLE IF NOT EXISTS `agent_run_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`    VARCHAR(64)  DEFAULT NULL COMMENT '关联 chat_session.id',
    `user_id`       BIGINT       NOT NULL COMMENT '学生用户ID',
    `agent`         VARCHAR(32)  NOT NULL COMMENT '智能体标识',
    `intent`        VARCHAR(64)  DEFAULT NULL COMMENT '协调智能体识别的意图',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    `duration_ms`   INT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    `detail`        TEXT         DEFAULT NULL COMMENT '步骤详情（JSON）',
    `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_session` (`session_id`),
    KEY `idx_agent_status` (`agent`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体运行日志表';

-- ========== 旧库增量补丁（须在 CREATE 之后；新库多为 no-op）==========

-- 13. 上传分析历史
CREATE TABLE IF NOT EXISTS `upload_analysis_history` (
    `id`                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`                  BIGINT        NOT NULL COMMENT '用户ID',
    `upload_task_id`           BIGINT        DEFAULT NULL COMMENT '关联上传任务ID',
    `file_name`                VARCHAR(256)  NOT NULL COMMENT '文件名',
    `file_path`                VARCHAR(512)  DEFAULT NULL COMMENT '文件存储路径或对象key',
    `summary`                  TEXT          DEFAULT NULL COMMENT '内容摘要',
    `mapped_kp_ids`            TEXT          DEFAULT NULL COMMENT '映射知识点ID列表（JSON数组）',
    `matched_knowledge_points` TEXT          DEFAULT NULL COMMENT '知识点匹配详情（JSON）',
    `status`                   VARCHAR(16)   NOT NULL DEFAULT 'COMPLETED' COMMENT '分析状态：COMPLETED/FAILED',
    `error_message`            VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    `created_at`               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `finished_at`              DATETIME      DEFAULT NULL COMMENT '分析完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_history_user_created` (`user_id`, `created_at`),
    KEY `idx_history_task` (`upload_task_id`),
    KEY `idx_history_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传分析历史表';

SET @create_upload_analysis_history := (
    SELECT COUNT(*) = 0 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'upload_analysis_history'
);
SET @sql_create_upload_analysis_history := IF(
    @create_upload_analysis_history,
    'CREATE TABLE `upload_analysis_history` (
        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT ''主键ID'',
        `user_id` BIGINT NOT NULL COMMENT ''用户ID'',
        `upload_task_id` BIGINT DEFAULT NULL COMMENT ''关联上传任务ID'',
        `file_name` VARCHAR(256) NOT NULL COMMENT ''文件名'',
        `file_path` VARCHAR(512) DEFAULT NULL COMMENT ''文件存储路径或对象key'',
        `summary` TEXT DEFAULT NULL COMMENT ''内容摘要'',
        `mapped_kp_ids` TEXT DEFAULT NULL COMMENT ''映射知识点ID列表（JSON数组）'',
        `matched_knowledge_points` TEXT DEFAULT NULL COMMENT ''知识点匹配详情（JSON）'',
        `status` VARCHAR(16) NOT NULL DEFAULT ''COMPLETED'' COMMENT ''分析状态：COMPLETED/FAILED'',
        `error_message` VARCHAR(1024) DEFAULT NULL COMMENT ''失败原因'',
        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
        `finished_at` DATETIME DEFAULT NULL COMMENT ''分析完成时间'',
        PRIMARY KEY (`id`),
        KEY `idx_history_user_created` (`user_id`, `created_at`),
        KEY `idx_history_task` (`upload_task_id`),
        KEY `idx_history_status` (`status`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''上传分析历史表''',
    'SELECT ''skip: upload_analysis_history exists'''
);
PREPARE stmt_create_upload_analysis_history FROM @sql_create_upload_analysis_history;
EXECUTE stmt_create_upload_analysis_history;
DEALLOCATE PREPARE stmt_create_upload_analysis_history;

-- learning_path_step.status
SET @add_step_status := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'learning_path_step'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_path_step'
      AND column_name = 'status'
);
SET @sql_add_step_status := IF(
    @add_step_status,
    'ALTER TABLE `learning_path_step` ADD COLUMN `status` VARCHAR(32) DEFAULT ''PENDING'' COMMENT ''步骤状态：PENDING/ACTIVE/COMPLETED/SKIPPED'' AFTER `completed`',
    'SELECT ''skip: learning_path_step.status exists or table missing'''
);
PREPARE stmt_add_step_status FROM @sql_add_step_status;
EXECUTE stmt_add_step_status;
DEALLOCATE PREPARE stmt_add_step_status;

-- kb_upload_task：内容分析结果（旧表缺列时追加）
SET @add_analysis_result := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kb_upload_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kb_upload_task'
      AND column_name = 'analysis_result'
);
SET @sql_add_analysis_result := IF(
    @add_analysis_result,
    'ALTER TABLE `kb_upload_task` ADD COLUMN `analysis_result` TEXT NULL COMMENT ''内容分析智能体输出（JSON）'' AFTER `error_message`',
    'SELECT ''skip: kb_upload_task.analysis_result exists or table missing'''
);
PREPARE stmt_add_analysis_result FROM @sql_add_analysis_result;
EXECUTE stmt_add_analysis_result;
DEALLOCATE PREPARE stmt_add_analysis_result;

SET @add_status_message := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kb_upload_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kb_upload_task'
      AND column_name = 'status_message'
);
SET @sql_add_status_message := IF(
    @add_status_message,
    'ALTER TABLE `kb_upload_task` ADD COLUMN `status_message` VARCHAR(512) NULL COMMENT ''浠诲姟鐘舵€佹彁绀轰俊鎭紙濡傞樁娈点€佽繘搴︼級'' AFTER `error_message`',
    'SELECT ''skip: kb_upload_task.status_message exists or table missing'''
);
PREPARE stmt_add_status_message FROM @sql_add_status_message;
EXECUTE stmt_add_status_message;
DEALLOCATE PREPARE stmt_add_status_message;

SET @add_progress_percent := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kb_upload_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kb_upload_task'
      AND column_name = 'progress_percent'
);
SET @sql_add_progress_percent := IF(
    @add_progress_percent,
    'ALTER TABLE `kb_upload_task` ADD COLUMN `progress_percent` INT NULL COMMENT ''浠诲姟杩涘害鐧惧垎姣?'' AFTER `status_message`',
    'SELECT ''skip: kb_upload_task.progress_percent exists or table missing'''
);
PREPARE stmt_add_progress_percent FROM @sql_add_progress_percent;
EXECUTE stmt_add_progress_percent;
DEALLOCATE PREPARE stmt_add_progress_percent;

SET @add_mapped_kp_ids := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kb_upload_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kb_upload_task'
      AND column_name = 'mapped_kp_ids'
);
SET @sql_add_mapped_kp_ids := IF(
    @add_mapped_kp_ids,
    'ALTER TABLE `kb_upload_task` ADD COLUMN `mapped_kp_ids` TEXT NULL COMMENT ''映射到的知识点ID列表（JSON数组）'' AFTER `analysis_result`',
    'SELECT ''skip: kb_upload_task.mapped_kp_ids exists or table missing'''
);
PREPARE stmt_add_mapped_kp_ids FROM @sql_add_mapped_kp_ids;
EXECUTE stmt_add_mapped_kp_ids;
DEALLOCATE PREPARE stmt_add_mapped_kp_ids;

-- resource_item：多模态媒体字段（生图/生视频结果 URL）
SET @add_resource_media_url := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'resource_item'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'resource_item'
      AND column_name = 'media_url'
);
SET @sql_add_resource_media_url := IF(
    @add_resource_media_url,
    'ALTER TABLE `resource_item` ADD COLUMN `media_url` VARCHAR(1024) NULL COMMENT ''媒体资源访问地址（图/视频，本地路径或对象存储 URL）'' AFTER `content`',
    'SELECT ''skip: resource_item.media_url exists or table missing'''
);
PREPARE stmt_add_resource_media_url FROM @sql_add_resource_media_url;
EXECUTE stmt_add_resource_media_url;
DEALLOCATE PREPARE stmt_add_resource_media_url;

SET @add_resource_media_mime := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'resource_item'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'resource_item'
      AND column_name = 'media_mime'
);
SET @sql_add_resource_media_mime := IF(
    @add_resource_media_mime,
    'ALTER TABLE `resource_item` ADD COLUMN `media_mime` VARCHAR(64) NULL COMMENT ''媒体 MIME，如 image/png、video/mp4'' AFTER `media_url`',
    'SELECT ''skip: resource_item.media_mime exists or table missing'''
);
PREPARE stmt_add_resource_media_mime FROM @sql_add_resource_media_mime;
EXECUTE stmt_add_resource_media_mime;
DEALLOCATE PREPARE stmt_add_resource_media_mime;

SET @add_resource_prompt := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'resource_item'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'resource_item'
      AND column_name = 'generation_prompt'
);
SET @sql_add_resource_prompt := IF(
    @add_resource_prompt,
    'ALTER TABLE `resource_item` ADD COLUMN `generation_prompt` TEXT NULL COMMENT ''用于生图/生视频的提示词（便于审计与重试）'' AFTER `media_mime`',
    'SELECT ''skip: resource_item.generation_prompt exists or table missing'''
);
PREPARE stmt_add_resource_prompt FROM @sql_add_resource_prompt;
EXECUTE stmt_add_resource_prompt;
DEALLOCATE PREPARE stmt_add_resource_prompt;

SET @add_quiz_record_question_id := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'quiz_record'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'quiz_record'
      AND column_name = 'question_id'
);
SET @sql_add_quiz_record_question_id := IF(
    @add_quiz_record_question_id,
    'ALTER TABLE `quiz_record` ADD COLUMN `question_id` BIGINT NULL COMMENT ''题目ID'' AFTER `user_id`',
    'SELECT ''skip: quiz_record.question_id exists or table missing'''
);
PREPARE stmt_add_quiz_record_question_id FROM @sql_add_quiz_record_question_id;
EXECUTE stmt_add_quiz_record_question_id;
DEALLOCATE PREPARE stmt_add_quiz_record_question_id;

-- agent_prompt: prompt_content/default_content 需支持长文本（从旧 VARCHAR 升级为 LONGTEXT）
SET @fix_prompt_columns := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'agent_prompt'
) AND (
    SELECT DATA_TYPE != 'longtext' FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_prompt'
      AND column_name = 'prompt_content'
    LIMIT 1
);
SET @sql_fix_prompt_columns := IF(
    @fix_prompt_columns,
    'ALTER TABLE `agent_prompt` MODIFY COLUMN `prompt_content` LONGTEXT NOT NULL COMMENT ''提示词内容（Markdown 格式）'', MODIFY COLUMN `default_content` LONGTEXT NULL COMMENT ''默认提示词（从文件加载，用于重置）''',
    'SELECT ''skip: agent_prompt columns already longtext'''
);
PREPARE stmt_fix_prompt_columns FROM @sql_fix_prompt_columns;
EXECUTE stmt_fix_prompt_columns;
DEALLOCATE PREPARE stmt_fix_prompt_columns;

-- pipeline_task.failed_step / pipeline_config_json（JPA ddl-auto=update 也会加列，此处供旧库补丁）
SET @add_pipeline_failed_step := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'pipeline_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'pipeline_task'
      AND column_name = 'failed_step'
);
SET @sql_add_pipeline_failed_step := IF(
    @add_pipeline_failed_step,
    'ALTER TABLE `pipeline_task` ADD COLUMN `failed_step` VARCHAR(64) NULL COMMENT ''失败步骤ID（断点重试）'' AFTER `error_message`',
    'SELECT ''skip: pipeline_task.failed_step exists or table missing'''
);
PREPARE stmt_add_pipeline_failed_step FROM @sql_add_pipeline_failed_step;
EXECUTE stmt_add_pipeline_failed_step;
DEALLOCATE PREPARE stmt_add_pipeline_failed_step;

SET @add_pipeline_config_json := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'pipeline_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'pipeline_task'
      AND column_name = 'pipeline_config_json'
);
SET @sql_add_pipeline_config_json := IF(
    @add_pipeline_config_json,
    'ALTER TABLE `pipeline_task` ADD COLUMN `pipeline_config_json` TEXT NULL COMMENT ''Pipeline 配置快照（JSON）'' AFTER `failed_step`',
    'SELECT ''skip: pipeline_task.pipeline_config_json exists or table missing'''
);
PREPARE stmt_add_pipeline_config_json FROM @sql_add_pipeline_config_json;
EXECUTE stmt_add_pipeline_config_json;
DEALLOCATE PREPARE stmt_add_pipeline_config_json;

-- ========== Sprint 2: quiz session / favorite / mark + upload metadata ==========

CREATE TABLE IF NOT EXISTS `quiz_practice_session` (
    `id`           VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `user_id`      BIGINT       NOT NULL COMMENT '学生ID',
    `session_data` LONGTEXT     NOT NULL COMMENT '会话 JSON 快照',
    `status`       VARCHAR(32)  DEFAULT NULL COMMENT '会话状态',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_quiz_session_user` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题练习会话表';

CREATE TABLE IF NOT EXISTS `quiz_question_favorite` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT   NOT NULL COMMENT '学生ID',
    `question_id` BIGINT   NOT NULL COMMENT '题目ID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_question_fav` (`user_id`, `question_id`),
    KEY `idx_fav_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目收藏表';

CREATE TABLE IF NOT EXISTS `quiz_question_mark` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT   NOT NULL COMMENT '学生ID',
    `question_id` BIGINT   NOT NULL COMMENT '题目ID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标记时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_question_mark` (`user_id`, `question_id`),
    KEY `idx_mark_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目标记表';

SET @add_upload_file_size := (
    SELECT COUNT(*) = 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kb_upload_task'
) AND (
    SELECT COUNT(*) = 0 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kb_upload_task'
      AND column_name = 'file_size_bytes'
);
SET @sql_add_upload_file_size := IF(
    @add_upload_file_size,
    'ALTER TABLE `kb_upload_task`
        ADD COLUMN `file_size_bytes` BIGINT NULL COMMENT ''文件大小（字节）'' AFTER `vector_index_name`,
        ADD COLUMN `learning_path_id` VARCHAR(512) NULL COMMENT ''关联学习路径标识'' AFTER `file_size_bytes`,
        ADD COLUMN `manual_kp_ids` TEXT NULL COMMENT ''用户手动关联知识点（逗号分隔）'' AFTER `learning_path_id`,
        ADD COLUMN `tags` TEXT NULL COMMENT ''用户标签 JSON 数组'' AFTER `manual_kp_ids`',
    'SELECT ''skip: kb_upload_task metadata columns exist or table missing'''
);
PREPARE stmt_add_upload_file_size FROM @sql_add_upload_file_size;
EXECUTE stmt_add_upload_file_size;
DEALLOCATE PREPARE stmt_add_upload_file_size;
