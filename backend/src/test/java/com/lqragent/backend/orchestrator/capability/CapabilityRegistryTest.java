package com.lqragent.backend.orchestrator.capability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CapabilityRegistry 和 AgentCapability 单元测试
 */
class CapabilityRegistryTest {

    private CapabilityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CapabilityRegistry();
    }

    @Test
    void testRegisterAndFindById() {
        AgentCapability cap = AgentCapability.builder()
                .agentId("qa_agent")
                .displayName("智能问答")
                .description("答疑、RAG知识库检索")
                .actions(List.of("answer", "search"))
                .tags(Set.of("qa", "knowledge"))
                .build();

        registry.register(cap);
        assertEquals(1, registry.size());

        var found = registry.findById("qa_agent");
        assertTrue(found.isPresent());
        assertEquals("智能问答", found.get().displayName());
    }

    @Test
    void testFindByTag() {
        registry.register(AgentCapability.builder()
                .agentId("qa_agent").displayName("QA")
                .description("答疑").tags(Set.of("qa", "chat")).build());
        registry.register(AgentCapability.builder()
                .agentId("resource_agent").displayName("Resource")
                .description("资源生成").tags(Set.of("resource", "content")).build());

        var qaAgents = registry.findByTag("qa");
        assertEquals(1, qaAgents.size());
        assertEquals("qa_agent", qaAgents.get(0).agentId());
    }

    @Test
    void testFindByKeyword() {
        registry.register(AgentCapability.builder()
                .agentId("learning_path_agent").displayName("学习路径")
                .description("规划学习路径、制定学习计划").tags(Set.of("path", "learning")).build());
        registry.register(AgentCapability.builder()
                .agentId("qa_agent").displayName("QA")
                .description("答疑").tags(Set.of("qa")).build());

        var results = registry.findByKeyword("路径");
        assertEquals(1, results.size());
        assertEquals("learning_path_agent", results.get(0).agentId());
    }

    @Test
    void testMatchBestAgent() {
        registry.register(AgentCapability.builder()
                .agentId("learning_path_agent").displayName("学习路径")
                .description("规划学习路径").tags(Set.of("learning_path", "path")).build());
        registry.register(AgentCapability.builder()
                .agentId("qa_agent").displayName("QA")
                .description("答疑").tags(Set.of("qa")).build());

        // 标签精确匹配优先
        assertEquals("learning_path_agent", registry.matchBestAgent("learning_path"));

        // 关键词模糊匹配
        assertEquals("learning_path_agent", registry.matchBestAgent("path"));

        // 无匹配时返回 qa_agent
        assertEquals("qa_agent", registry.matchBestAgent("nonexistent"));
    }

    @Test
    void testBuildCapabilityCatalog() {
        registry.register(AgentCapability.builder()
                .agentId("qa_agent").displayName("QA")
                .description("答疑、RAG知识库检索、流式对话")
                .actions(List.of("answer_question", "search_knowledge"))
                .tags(Set.of("qa", "chat"))
                .build());

        String catalog = registry.buildCapabilityCatalog();
        assertTrue(catalog.contains("qa_agent"));
        assertTrue(catalog.contains("答疑"));
        assertTrue(catalog.contains("answer_question"));
        assertTrue(catalog.contains("qa"));
    }

    @Test
    void testUnregister() {
        registry.register(AgentCapability.builder()
                .agentId("test_agent").displayName("Test").description("test").build());
        assertEquals(1, registry.size());

        registry.unregister("test_agent");
        assertEquals(0, registry.size());
        assertTrue(registry.findById("test_agent").isEmpty());
    }

    @Test
    void testMultipleAgents() {
        registry.registerAll(List.of(
                AgentCapability.builder().agentId("a1").displayName("A1").description("d1")
                        .tags(Set.of("tag1")).build(),
                AgentCapability.builder().agentId("a2").displayName("A2").description("d2")
                        .tags(Set.of("tag2")).build(),
                AgentCapability.builder().agentId("a3").displayName("A3").description("d3")
                        .tags(Set.of("tag1", "tag3")).build()
        ));

        assertEquals(3, registry.size());
        assertEquals(Set.of("a1", "a2", "a3"), registry.getAllAgentIds());
        assertEquals(2, registry.findByTag("tag1").size());
    }

    @Test
    void testEmptyRegistry() {
        assertEquals("暂无可用 Agent", registry.buildCapabilityCatalog());
        assertEquals("qa_agent", registry.matchBestAgent("anything"));
        assertTrue(registry.getAllAgentIds().isEmpty());
    }
}
