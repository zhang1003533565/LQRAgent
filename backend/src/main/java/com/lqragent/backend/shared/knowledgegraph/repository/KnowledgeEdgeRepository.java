package com.lqragent.backend.shared.knowledgegraph.repository;

import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgeEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeEdgeRepository extends JpaRepository<KnowledgeEdge, Long> {

    /** 查某个知识点的所有前置依赖 */
    List<KnowledgeEdge> findByToKpId(String toKpId);

    /** 查以某个知识点为前置的所有后置知识点 */
    List<KnowledgeEdge> findByFromKpId(String fromKpId);
}
