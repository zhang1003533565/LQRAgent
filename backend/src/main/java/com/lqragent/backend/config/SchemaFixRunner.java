package com.lqragent.backend.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用启动完成后自动检测并修复数据库表结构问题。
 * 使用 ApplicationReadyEvent 确保在 JPA 初始化之后执行。
 * 幂等操作，可重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaFixRunner {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("[SchemaFixRunner] 开始检查数据库表结构...");
        fixAutoIncrement("chat_session");
        fixAutoIncrement("chat_message");
        fixChatMessageBodyColumn();
        fixAgentPromptDefaultContentColumn();
        log.info("[SchemaFixRunner] 数据库表结构检查完成");
    }

    /**
     * 确保指定表的 id 字段具有 AUTO_INCREMENT 属性。
     * 如果没有，则自动添加。
     */
    private void fixAutoIncrement(String tableName) {
        try {
            // 检查表是否存在
            List<String> tables = jdbc.queryForList(
                    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class, tableName);
            
            if (tables.isEmpty()) {
                log.debug("[SchemaFixRunner] 表 {} 不存在，跳过", tableName);
                return;
            }

            // 检查 id 字段当前类型和 EXTRA
            List<Map<String, Object>> columns = jdbc.queryForList(
                    "SELECT COLUMN_TYPE, EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'id'",
                    tableName);

            if (columns.isEmpty()) {
                log.warn("[SchemaFixRunner] 表 {} 没有 id 字段，跳过", tableName);
                return;
            }

            Map<String, Object> col = columns.get(0);
            String columnType = (String) col.get("COLUMN_TYPE");
            String extra = (String) col.get("EXTRA");
            
            log.info("[SchemaFixRunner] 表 {} 的 id 字段: type={}, extra={}", tableName, columnType, extra);

            if (extra != null && extra.toUpperCase().contains("AUTO_INCREMENT")) {
                log.debug("[SchemaFixRunner] 表 {} 的 id 字段已有 AUTO_INCREMENT", tableName);
                return;
            }

            // 检查表中是否有数据
            Integer rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            
            if (rowCount != null && rowCount > 0) {
                // 有数据时，需要先清空再修改（危险操作，仅用于开发环境）
                log.warn("[SchemaFixRunner] 表 {} 有 {} 条数据，将清空后修复...", tableName, rowCount);
                jdbc.execute("TRUNCATE TABLE " + tableName);
            }

            // 修复：添加 AUTO_INCREMENT
            log.info("[SchemaFixRunner] 正在修复表 {} 的 id 字段，添加 AUTO_INCREMENT...", tableName);
            jdbc.execute("ALTER TABLE " + tableName + " MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT");
            log.info("[SchemaFixRunner] 表 {} 修复完成", tableName);

        } catch (Exception e) {
            log.error("[SchemaFixRunner] 修复表 {} 失败: {}", tableName, e.getMessage());
        }
    }

    /**
     * 修复 chat_message 表的 body 字段：实体类不再使用该字段，将其改为可空。
     */
    private void fixChatMessageBodyColumn() {
        try {
            List<Map<String, Object>> columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_message' AND COLUMN_NAME = 'body'");
            
            if (columns.isEmpty()) {
                log.debug("[SchemaFixRunner] chat_message 表无 body 字段，跳过");
                return;
            }

            // 将 body 字段改为可空（实体类不再使用此字段）
            log.info("[SchemaFixRunner] 修复 chat_message 表的 body 字段：改为可空...");
            jdbc.execute("ALTER TABLE chat_message MODIFY COLUMN body TEXT NULL");
            log.info("[SchemaFixRunner] chat_message 表的 body 字段修复完成");

        } catch (Exception e) {
            log.error("[SchemaFixRunner] 修复 chat_message body 字段失败: {}", e.getMessage());
        }
    }

    /**
     * 修复 agent_prompt 表的 default_content 和 prompt_content 字段为 TEXT 类型。
     * JPA @Lob 注解不会自动修改已存在的列类型。
     */
    private void fixAgentPromptDefaultContentColumn() {
        try {
            List<Map<String, Object>> columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_prompt' AND COLUMN_NAME IN ('default_content', 'prompt_content')");

            if (columns.isEmpty()) {
                log.debug("[SchemaFixRunner] agent_prompt 表不存在，跳过");
                return;
            }

            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("COLUMN_NAME");
                String colType = (String) col.get("COLUMN_TYPE");
                if (!"text".equalsIgnoreCase(colType) && !"longtext".equalsIgnoreCase(colType)) {
                    log.info("[SchemaFixRunner] 修复 agent_prompt 表的 {} 字段：{} -> TEXT", colName, colType);
                    jdbc.execute("ALTER TABLE agent_prompt MODIFY COLUMN " + colName + " TEXT NOT NULL");
                }
            }
            log.info("[SchemaFixRunner] agent_prompt 表修复完成");
        } catch (Exception e) {
            log.error("[SchemaFixRunner] 修复 agent_prompt 表失败: {}", e.getMessage());
        }
    }
}
