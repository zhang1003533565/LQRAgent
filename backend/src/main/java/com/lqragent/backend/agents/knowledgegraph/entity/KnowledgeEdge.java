package com.lqragent.backend.agents.knowledgegraph.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "knowledge_edge")
@Comment("知识点依赖关系表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "from_kp_id", nullable = false, length = 64)
    @Comment("前置知识点ID")
    private String fromKpId;

    @Column(name = "to_kp_id", nullable = false, length = 64)
    @Comment("后置知识点ID")
    private String toKpId;

    @Column(name = "relation_type", length = 32)
    @Comment("关系类型：PREREQUISITE等")
    @Builder.Default
    private String relationType = "PREREQUISITE";
}
