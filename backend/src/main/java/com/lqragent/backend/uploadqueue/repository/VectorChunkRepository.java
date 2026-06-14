package com.lqragent.backend.uploadqueue.repository;

import com.lqragent.backend.uploadqueue.entity.VectorChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorChunkRepository extends JpaRepository<VectorChunk, Long> {

    /**
     * 根据任务ID查询所有向量块
     */
    List<VectorChunk> findByTaskIdOrderByChunkIndexAsc(Long taskId);

    /**
     * 根据任务ID分页查询向量块
     */
    Page<VectorChunk> findByTaskIdOrderByChunkIndexAsc(Long taskId, Pageable pageable);

    /**
     * 根据索引名称查询向量块
     */
    List<VectorChunk> findByIndexNameOrderByChunkIndexAsc(String indexName);

    /**
     * 根据任务ID统计向量块数量
     */
    long countByTaskId(Long taskId);

    /**
     * 根据任务ID删除所有向量块
     */
    @Modifying
    @Query("DELETE FROM VectorChunk c WHERE c.taskId = :taskId")
    void deleteByTaskId(Long taskId);

    /**
     * 根据索引名称删除所有向量块
     */
    @Modifying
    @Query("DELETE FROM VectorChunk c WHERE c.indexName = :indexName")
    void deleteByIndexName(String indexName);

    /**
     * 根据知识点ID查询向量块
     */
    List<VectorChunk> findByKpIdOrderByChunkIndexAsc(String kpId);
}