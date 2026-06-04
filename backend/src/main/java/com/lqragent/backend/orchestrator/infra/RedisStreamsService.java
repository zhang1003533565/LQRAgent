package com.lqragent.backend.orchestrator.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis Streams 消息队列服务
 */
@Slf4j
@Service
public class RedisStreamsService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisStreamsService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 发送消息到指定 stream
     */
    public String send(String stream, AgentMessage msg) {
        try {
            Map<String, String> fields = serialize(msg);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofStrings(fields)
                    .withStreamKey(stream);
            RecordId recordId = redis.opsForStream().add(record);
            String id = recordId != null ? recordId.getValue() : null;
            log.debug("[Streams] sent to {}: {}", stream, id);
            return id;
        } catch (Exception e) {
            log.error("[Streams] send failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建消费者组（幂等）
     */
    public void createGroup(String stream, String group) {
        try {
            redis.opsForStream().createGroup(stream, group);
            log.info("[Streams] group created: {} @ {}", group, stream);
        } catch (Exception e) {
            // Group already exists - ignore
        }
    }

    /**
     * 消费消息（从上次消费位置继续）
     */
    public List<AgentMessage> consume(String stream, String group, String consumer, int count) {
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(count).block(Duration.ofSeconds(5)),
                    StreamOffset.create(stream, ReadOffset.from(">"))
            );
            if (records == null || records.isEmpty()) {
                return Collections.emptyList();
            }
            return records.stream()
                    .map(r -> {
                        redis.opsForStream().acknowledge(stream, group, r.getId());
                        return deserialize(r.getValue());
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[Streams] consume failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 读取新消息
     */
    public List<AgentMessage> consumePending(String stream, String group, String consumer, int count) {
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(count),
                    StreamOffset.create(stream, ReadOffset.from(">"))
            );
            if (records == null || records.isEmpty()) {
                return Collections.emptyList();
            }
            return records.stream()
                    .map(r -> {
                        redis.opsForStream().acknowledge(stream, group, r.getId());
                        return deserialize(r.getValue());
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[Streams] consumePending failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, String> serialize(AgentMessage msg) {
        Map<String, String> map = new HashMap<>();
        map.put("id", msg.getId());
        map.put("taskId", msg.getTaskId());
        map.put("performative", msg.getPerformative().name());
        map.put("sender", msg.getSender());
        map.put("receiver", msg.getReceiver());
        map.put("timestamp", String.valueOf(msg.getTimestamp()));
        if (msg.getInReplyTo() != null) map.put("inReplyTo", msg.getInReplyTo());
        if (msg.getConversationId() != null) map.put("conversationId", msg.getConversationId());
        try {
            map.put("content", mapper.writeValueAsString(msg.getContent()));
        } catch (JsonProcessingException e) {
            map.put("content", "{}");
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private AgentMessage deserialize(Map<Object, Object> map) {
        try {
            Map<String, String> fields = new HashMap<>();
            map.forEach((k, v) -> fields.put(k.toString(), v.toString()));
            Map<String, Object> content = mapper.readValue(
                    fields.getOrDefault("content", "{}"), Map.class);
            return AgentMessage.builder()
                    .id(fields.get("id"))
                    .taskId(fields.get("taskId"))
                    .performative(Performative.valueOf(fields.get("performative")))
                    .sender(fields.get("sender"))
                    .receiver(fields.get("receiver"))
                    .content(content)
                    .timestamp(Long.parseLong(fields.getOrDefault("timestamp", "0")))
                    .inReplyTo(fields.get("inReplyTo"))
                    .conversationId(fields.get("conversationId"))
                    .build();
        } catch (Exception e) {
            log.error("[Streams] deserialize failed: {}", e.getMessage());
            return null;
        }
    }
}
