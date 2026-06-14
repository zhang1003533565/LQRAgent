package com.lqragent.backend.uploadqueue.service;

import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.VectorChunk;
import com.lqragent.backend.uploadqueue.repository.KbUploadTaskRepository;
import com.lqragent.backend.uploadqueue.repository.VectorChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorChunkService {

    private final VectorChunkRepository vectorChunkRepository;
    private final KbUploadTaskRepository taskRepository;
    private final AiServerClient aiServerClient;
    private final AppRuntimeConfig runtimeConfig;

    /**
     * 保存单个向量块
     */
    public VectorChunk save(VectorChunk chunk) {
        return vectorChunkRepository.save(chunk);
    }

    /**
     * 批量保存向量块
     */
    public List<VectorChunk> saveAll(List<VectorChunk> chunks) {
        return vectorChunkRepository.saveAll(chunks);
    }

    /**
     * 根据ID查询向量块
     */
    public Optional<VectorChunk> findById(Long id) {
        return vectorChunkRepository.findById(id);
    }

    /**
     * 根据任务ID查询所有向量块
     */
    public List<VectorChunk> findByTaskId(Long taskId) {
        List<VectorChunk> chunks = vectorChunkRepository.findByTaskIdOrderByChunkIndexAsc(taskId);
        if (!chunks.isEmpty()) {
            return chunks;
        }
        backfillChunks(taskId);
        return vectorChunkRepository.findByTaskIdOrderByChunkIndexAsc(taskId);
    }

    @Transactional
    public void backfillChunks(Long taskId) {
        KbUploadTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != KbUploadTask.TaskStatus.COMPLETED) {
            return;
        }

        String kbName = task.getKbScope() == KbUploadTask.KbScope.PUBLIC
                ? runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public")
                : runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-") + task.getUserId();

        try {
            List<Map<String, Object>> sourceChunks = aiServerClient.getDocumentChunks(kbName, task.getFileName());
            if (sourceChunks.isEmpty()) {
                return;
            }

            int chunkCount = 0;
            long tokenCount = 0;
            for (Map<String, Object> chunkData : sourceChunks) {
                String content = normalizeText(asText(chunkData.get("content")));
                if (content == null || content.isBlank()) {
                    continue;
                }
                Integer tokens = asInteger(chunkData.get("token_count"));
                vectorChunkRepository.save(VectorChunk.builder()
                        .taskId(task.getId())
                        .indexName(kbName)
                        .chunkIndex(asInteger(chunkData.get("chunk_index")) != null ? asInteger(chunkData.get("chunk_index")) : chunkCount)
                        .content(content)
                        .tokenCount(tokens != null ? tokens : 0)
                        .metadata(normalizeText(asText(chunkData.get("metadata"))))
                        .kpId(asText(chunkData.get("kp_id")))
                        .build());
                chunkCount++;
                tokenCount += tokens != null ? tokens : 0;
            }
            task.setVectorChunkCount(chunkCount);
            task.setVectorTotalTokens(tokenCount);
            task.setVectorIndexName(kbName);
            taskRepository.save(task);
            log.info("[VectorChunk] Backfilled {} chunks for task {}", chunkCount, taskId);
        } catch (Exception e) {
            log.warn("[VectorChunk] Backfill failed for task {}: {}", taskId, e.getMessage());
        }
    }

    /**
     * 根据任务ID分页查询向量块
     */
    public Page<VectorChunk> findByTaskId(Long taskId, Pageable pageable) {
        return vectorChunkRepository.findByTaskIdOrderByChunkIndexAsc(taskId, pageable);
    }

    /**
     * 根据索引名称查询向量块
     */
    public List<VectorChunk> findByIndexName(String indexName) {
        return vectorChunkRepository.findByIndexNameOrderByChunkIndexAsc(indexName);
    }

    /**
     * 根据任务ID统计向量块数量
     */
    public long countByTaskId(Long taskId) {
        return vectorChunkRepository.countByTaskId(taskId);
    }

    /**
     * 删除单个向量块
     */
    @Transactional
    public boolean deleteById(Long id) {
        if (vectorChunkRepository.existsById(id)) {
            vectorChunkRepository.deleteById(id);
            log.info("[VectorChunk] 删除向量块: id={}", id);
            return true;
        }
        return false;
    }

    /**
     * 根据任务ID删除所有向量块
     */
    @Transactional
    public void deleteByTaskId(Long taskId) {
        vectorChunkRepository.deleteByTaskId(taskId);
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setVectorChunkCount(0);
            task.setVectorTotalTokens(0L);
            task.setVectorIndexName(null);
            taskRepository.save(task);
        });
        log.info("[VectorChunk] 删除任务关联的向量块: taskId={}", taskId);
    }

    /**
     * 根据索引名称删除所有向量块
     */
    @Transactional
    public void deleteByIndexName(String indexName) {
        vectorChunkRepository.deleteByIndexName(indexName);
        log.info("[VectorChunk] 删除索引关联的向量块: indexName={}", indexName);
    }

    /**
     * 根据知识点ID查询向量块
     */
    public List<VectorChunk> findByKpId(String kpId) {
        return vectorChunkRepository.findByKpIdOrderByChunkIndexAsc(kpId);
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeText(String value) {
        if (value == null || !looksLikeMojibake(value)) {
            return value;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int encodedChars = 0;
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch <= 255) {
                    bytes.write(ch);
                    if (ch >= 128) {
                        encodedChars++;
                    }
                } else {
                    bytes.write(String.valueOf(ch).getBytes(StandardCharsets.UTF_8));
                }
            }
            if (encodedChars == 0) {
                return value;
            }
            String decoded = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            return containsCjk(decoded) ? decoded : value;
        } catch (Exception ignored) {
            return value;
        }
    }

    private boolean containsCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeMojibake(String value) {
        return value.indexOf('Ã') >= 0 || value.indexOf('Â') >= 0 || value.indexOf('æ') >= 0
                || value.indexOf('ç') >= 0 || value.indexOf('è') >= 0 || value.indexOf('å') >= 0;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}