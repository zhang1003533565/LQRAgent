package com.lqragent.backend.orchestrator.card;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lqragent.backend.orchestrator.AgentIds;

import static org.junit.jupiter.api.Assertions.*;

class AgentCardRegistryTest {

    private AgentCardRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentCardRegistry();
    }

    private AgentCard qaCard() {
        return AgentCard.simple(
                AgentIds.QA,
                "智能问答",
                "解答学习问题、知识检索",
                List.of("qa", "question", "answer"),
                List.of("text", "rag_sources"));
    }

    private AgentCard pathCard() {
        return AgentCard.simple(
                AgentIds.LEARNING_PATH,
                "学习路径",
                "规划学习路径、制定学习计划",
                List.of("learning_path", "path"),
                List.of("learning_path"));
    }

    @Test
    void testRegisterAndFindById() {
        registry.register(qaCard());
        assertEquals(1, registry.size());
        assertTrue(registry.findById(AgentIds.QA).isPresent());
        assertEquals("智能问答", registry.findById(AgentIds.QA).get().displayName());
    }

    @Test
    void testFindByTag() {
        registry.register(qaCard());
        registry.register(pathCard());

        assertEquals(1, registry.findByTag("qa").size());
        assertEquals(AgentIds.QA, registry.findByTag("qa").get(0).agentId());
    }

    @Test
    void testFindByKeyword() {
        registry.register(qaCard());
        registry.register(pathCard());

        assertEquals(1, registry.findByKeyword("路径").size());
        assertEquals(AgentIds.LEARNING_PATH, registry.findByKeyword("路径").get(0).agentId());
    }

    @Test
    void testMatchBestAgent() {
        registry.register(qaCard());
        registry.register(pathCard());

        assertEquals(AgentIds.LEARNING_PATH, registry.matchBestAgent("learning_path"));
        assertEquals(AgentIds.LEARNING_PATH, registry.matchBestAgent("path"));
        assertEquals(AgentIds.QA, registry.matchBestAgent("nonexistent"));
    }

    @Test
    void testBuildCatalogAndHelp() {
        registry.register(qaCard());

        String catalog = registry.buildCatalog();
        assertTrue(catalog.contains(AgentIds.QA));
        assertTrue(catalog.contains("解答学习问题"));

        String help = registry.buildHelpMessage();
        assertTrue(help.contains("我可以帮你做这些事情"));
    }

    @Test
    void testEmptyRegistry() {
        assertEquals("暂无可用 Agent", registry.buildCatalog());
        assertEquals(AgentIds.QA, registry.matchBestAgent("anything"));
        assertTrue(registry.getAllAgentIds().isEmpty());
    }
}
