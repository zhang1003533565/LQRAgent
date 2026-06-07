# LQRAgent 项目完善计划

> 最后更新: 2026-06-07

---

## 目录

1. [项目现状概览](#1-项目现状概览)
2. [Phase 1: 基础架构增强](#2-phase-1-基础架构增强)
3. [Phase 2: Pipeline 流水线引擎](#3-phase-2-pipeline-流水线引擎)
4. [Phase 3: 记忆系统与聊天历史](#4-phase-3-记忆系统与聊天历史)
5. [Phase 4: 前端增强](#5-phase-4-前端增强)
6. [Phase 5: 测试与优化](#6-phase-5-测试与优化)
7. [总体时间表](#7-总体时间表)
8. [技术栈确认](#8-技术栈确认)

---

## 1. 项目现状概览

### 1.1 已完成功能

| 模块 | 状态 | 说明 |
|------|------|------|
| OrchestratorCore | ✅ 已实现 | 调度中枢，意图识别，任务分发 |
| Agent 框架 | ✅ 已实现 | 15+ 智能体，BaseAgent 抽象类 |
| AgentMemory | ⚠️ 基础版 | 仅内存存储，单用户 |
| AgentMessage | ✅ 已实现 | FIPA ACL 消息格式 |
| RedisStreams | ✅ 已实现 | 消息队列通信 |
| AgentRunLog | ⚠️ 基础版 | 无链路追踪 |
| 前端聊天 | ⚠️ 基础版 | 单会话，无历史 |

### 1.2 待完善重点

```
┌─────────────────────────────────────────────────────────────┐
│                    待完善功能清单                              │
├─────────────────────────────────────────────────────────────┤
│  1. Pipeline 引擎 - 替代硬编码的任务流                         │
│  2. TaskContext - 共享任务上下文                              │
│  3. 链路追踪 - 可视化 Agent 调用链                            │
│  4. 多用户记忆 - 独立记忆空间 + 持久化                         │
│  5. 聊天历史 - 会话管理、历史查看、新建会话                      │
│  6. 前端优化 - 侧边栏、记忆面板、Trace 可视化                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Phase 1: 基础架构增强

### 2.1 扩展 Performative 枚举

**文件**: `backend/.../orchestrator/message/Performative.java`

```java
public enum Performative {
    // 现有
    REQUEST, INFORM, PROGRESS, ERROR,
    // 新增
    NEGOTIATE,   // 协商：Agent 间协作决策
    REFUSE,      // 拒绝：Agent 无法完成任务
    CFP,         // Call for Proposal：招标式任务分配
    PROPOSE,     // 提案：响应 CFP
    CONFIRM      // 确认：任务完成确认
}
```

### 2.2 AgentIds 新增

**文件**: `backend/.../orchestrator/AgentIds.java`

```java
// 新增智能体
public static final String PROMPT_GEN = "prompt_gen_agent";   // Prompt 生成
public static final String MEDIA_GEN = "media_gen_agent";     // 媒体生成
```

### 2.3 TaskContext 共享上下文

**新建文件**: `backend/.../orchestrator/context/TaskContext.java`

```java
@Data
public class TaskContext {
    private final String taskId;
    private final String userId;
    private final String sessionId;
    private final String goal;
    private final Instant createdAt;
    
    // 共享数据
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
    
    // Agent 间传递的中间结果
    private final Map<String, Map<String, Object>> agentResults = new ConcurrentHashMap<>();
    
    // 链路追踪
    private final List<TraceSpan> traceSpans = new CopyOnWriteArrayList<>();
    
    public void setResult(String agentId, Map<String, Object> result) {
        agentResults.put(agentId, result);
    }
    
    public Map<String, Object> getResult(String agentId) {
        return agentResults.get(agentId);
    }
    
    public void addTraceSpan(TraceSpan span) {
        traceSpans.add(span);
    }
}
```

### 2.4 AgentRunLog 链路追踪字段

**修改文件**: `backend/.../chat/service/AgentRunLogService.java`

```java
// 新增字段
private String traceId;        // 链路ID（同一任务共享）
private String parentSpanId;   // 父跨度ID
private String spanId;         // 当前跨度ID
private Long durationMs;       // 执行耗时
private String inputSummary;   // 输入摘要
private String outputSummary;  // 输出摘要
```

---

## 3. Phase 2: Pipeline 流水线引擎

### 3.1 PipelineStep + PipelineConfig 模型

**新建文件**: `backend/.../orchestrator/pipeline/PipelineStep.java`

```java
@Data
public class PipelineStep {
    private String stepId;
    private String agentId;
    private String action;
    private Map<String, Object> params;
    
    // 依赖关系
    private List<String> dependsOn;      // 依赖的 stepId 列表
    private ConditionType conditionType;  // ALL_COMPLETED / ANY_COMPLETED
    
    // 执行策略
    private int maxRetries = 3;
    private long timeoutMs = 30000;
    private boolean optional = false;    // 可选步骤，失败不阻断
}
```

**新建文件**: `backend/.../orchestrator/pipeline/PipelineConfig.java`

```java
@Data
public class PipelineConfig {
    private String pipelineId;
    private String name;
    private String description;
    private List<PipelineStep> steps;
    
    // 全局配置
    private long totalTimeoutMs = 120000;
    private boolean parallel = true;  // 是否并行执行无依赖的步骤
}
```

### 3.2 PipelineEngine 引擎

**新建文件**: `backend/.../orchestrator/pipeline/PipelineEngine.java`

```java
@Slf4j
@Service
public class PipelineEngine {
    
    private final RedisStreamsService streams;
    private final Map<String, PipelineConfig> templates = new ConcurrentHashMap<>();
    
    /**
     * 执行 Pipeline
     */
    public PipelineResult execute(PipelineConfig config, TaskContext context) {
        String traceId = context.getTaskId();
        log.info("[Pipeline] start: {}, steps={}", config.getName(), config.getSteps().size());
        
        // 构建执行图
        ExecutionGraph graph = buildGraph(config);
        
        // 按拓扑顺序执行
        while (graph.hasReadySteps()) {
            List<PipelineStep> readySteps = graph.getReadySteps();
            
            if (config.isParallel()) {
                // 并行执行
                executeParallel(readySteps, context);
            } else {
                // 串行执行
                for (PipelineStep step : readySteps) {
                    executeStep(step, context);
                }
            }
        }
        
        return buildResult(context);
    }
    
    /**
     * 执行单个步骤
     */
    private StepResult executeStep(PipelineStep step, TaskContext context) {
        TraceSpan span = new TraceSpan(step.getStepId(), step.getAgentId());
        context.addTraceSpan(span);
        
        try {
            // 发送任务到 Agent
            AgentMessage msg = AgentMessage.request(
                context.getTaskId(),
                "pipeline_engine",
                step.getAgentId(),
                buildPayload(step, context)
            );
            streams.send("stream:agent:" + step.getAgentId(), msg);
            
            // 等待结果（带超时）
            return waitForResult(context.getTaskId(), step, span);
            
        } catch (Exception e) {
            span.setError(e);
            if (!step.isOptional()) {
                throw new PipelineException("Step failed: " + step.getStepId(), e);
            }
            return StepResult.skipped(e);
        }
    }
}
```

### 3.3 PipelineTemplates 内置模板

**新建文件**: `backend/.../orchestrator/pipeline/PipelineTemplates.java`

```java
@Component
public class PipelineTemplates {
    
    /**
     * 学习路径生成流水线
     */
    public static PipelineConfig learningPath() {
        return PipelineConfig.builder()
            .pipelineId("learning_path")
            .name("学习路径生成")
            .steps(List.of(
                // Step 1: 获取用户画像
                PipelineStep.builder()
                    .stepId("profile")
                    .agentId(AgentIds.PROFILE)
                    .action("get_profile")
                    .build(),
                // Step 2: 生成路径（依赖画像）
                PipelineStep.builder()
                    .stepId("path_gen")
                    .agentId(AgentIds.LEARNING_PATH)
                    .action("generate_path")
                    .dependsOn(List.of("profile"))
                    .build(),
                // Step 3: 生成资源（依赖路径）
                PipelineStep.builder()
                    .stepId("resources")
                    .agentId(AgentIds.RESOURCE)
                    .action("batch_generate")
                    .dependsOn(List.of("path_gen"))
                    .build(),
                // Step 4: 质量评估（依赖资源）
                PipelineStep.builder()
                    .stepId("quality")
                    .agentId(AgentIds.QUALITY)
                    .action("check")
                    .dependsOn(List.of("resources"))
                    .build()
            ))
            .build();
    }
    
    /**
     * 问答流水线
     */
    public static PipelineConfig questionAnswer() {
        return PipelineConfig.builder()
            .pipelineId("qa")
            .name("智能问答")
            .steps(List.of(
                // 并行：知识检索 + 意图分析
                PipelineStep.builder()
                    .stepId("knowledge")
                    .agentId(AgentIds.QA)
                    .action("search_knowledge")
                    .build(),
                PipelineStep.builder()
                    .stepId("analysis")
                    .agentId(AgentIds.CONTENT_ANALYSIS)
                    .action("analyze")
                    .build(),
                // 生成答案（依赖两者）
                PipelineStep.builder()
                    .stepId("answer")
                    .agentId(AgentIds.QA)
                    .action("generate_answer")
                    .dependsOn(List.of("knowledge", "analysis"))
                    .build()
            ))
            .build();
    }
    
    /**
     * 媒体内容生成流水线
     */
    public static PipelineConfig mediaGeneration() {
        return PipelineConfig.builder()
            .pipelineId("media_gen")
            .name("媒体内容生成")
            .steps(List.of(
                // Step 1: 生成 Prompt
                PipelineStep.builder()
                    .stepId("prompt_gen")
                    .agentId(AgentIds.PROMPT_GEN)
                    .action("generate_prompt")
                    .build(),
                // Step 2: 生成媒体（依赖 Prompt）
                PipelineStep.builder()
                    .stepId("media_gen")
                    .agentId(AgentIds.MEDIA_GEN)
                    .action("generate_media")
                    .dependsOn(List.of("prompt_gen"))
                    .build()
            ))
            .build();
    }
}
```

---

## 4. Phase 3: 记忆系统与聊天历史

### 4.1 数据库表结构

```sql
-- 聊天会话表
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, ARCHIVED, DELETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status)
);

-- 聊天消息表
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(20) DEFAULT 'text',
    agent_name VARCHAR(50),
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user_created (user_id, created_at)
);

-- 用户长期记忆表
CREATE TABLE user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(50),
    importance INT DEFAULT 1,
    access_count INT DEFAULT 0,
    last_accessed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, memory_type),
    INDEX idx_user_importance (user_id, importance DESC)
);

-- 链路追踪表
CREATE TABLE trace_span (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(50) NOT NULL,
    span_id VARCHAR(50) NOT NULL,
    parent_span_id VARCHAR(50),
    agent_id VARCHAR(50) NOT NULL,
    step_id VARCHAR(50),
    status VARCHAR(20),  -- SUCCESS, ERROR, TIMEOUT
    duration_ms BIGINT,
    input_summary TEXT,
    output_summary TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trace (trace_id),
    INDEX idx_parent (parent_span_id)
);
```

### 4.2 核心实体类

**新建文件**: `backend/.../chat/entity/ChatSession.java`

```java
@Entity
@Table(name = "chat_session")
@Data
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    private String title;
    
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

public enum SessionStatus {
    ACTIVE, ARCHIVED, DELETED
}
```

**新建文件**: `backend/.../chat/entity/ChatMessage.java`

```java
@Entity
@Table(name = "chat_message")
@Data
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id")
    private Long sessionId;
    
    @Column(name = "user_id")
    private Long userId;
    
    private String role;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "content_type")
    private String contentType = "text";
    
    @Column(name = "agent_name")
    private String agentName;
    
    @Column(columnDefinition = "JSON")
    private String metadata;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

**新建文件**: `backend/.../chat/entity/UserMemory.java`

```java
@Entity
@Table(name = "user_memory")
@Data
public class UserMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type")
    private MemoryType memoryType;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String source;
    private Integer importance = 1;
    
    @Column(name = "access_count")
    private Integer accessCount = 0;
    
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

public enum MemoryType {
    PREFERENCE,           // 用户偏好
    LEARNING_PROGRESS,    // 学习进度
    TOPIC_INTEREST,       // 话题兴趣
    INTERACTION_STYLE,    // 交互风格
    KNOWLEDGE_STATE       // 知识掌握状态
}
```

### 4.3 Repository 层

**新建文件**: `backend/.../chat/repository/ChatSessionRepository.java`

```java
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, SessionStatus status);
    
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' ORDER BY s.updatedAt DESC")
    Page<ChatSession> findActiveSessions(Long userId, Pageable pageable);
}
```

**新建文件**: `backend/.../chat/repository/ChatMessageRepository.java`

```java
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentBySession(Long sessionId, Pageable pageable);
}
```

**新建文件**: `backend/.../chat/repository/UserMemoryRepository.java`

```java
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {
    List<UserMemory> findByUserIdAndMemoryType(Long userId, MemoryType type);
    
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.importance >= :minImportance ORDER BY m.importance DESC, m.lastAccessedAt DESC")
    List<UserMemory> findImportantMemories(Long userId, Integer minImportance);
    
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.content LIKE %:keyword%")
    List<UserMemory> searchMemories(Long userId, String keyword);
}
```

### 4.4 Service 层

**新建文件**: `backend/.../chat/service/ChatHistoryService.java`

```java
@Service
@Transactional
public class ChatHistoryService {
    
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    
    /**
     * 创建新会话
     */
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : generateDefaultTitle());
        session.setStatus(SessionStatus.ACTIVE);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepo.save(session);
    }
    
    /**
     * 获取用户的会话列表
     */
    public List<ChatSession> getUserSessions(Long userId, int page, int size) {
        return sessionRepo.findActiveSessions(userId, PageRequest.of(page, size));
    }
    
    /**
     * 保存消息
     */
    public ChatMessage saveMessage(Long sessionId, Long userId, String role, 
                                   String content, String agentName) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setAgentName(agentName);
        message.setCreatedAt(LocalDateTime.now());
        
        // 更新会话
        ChatSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setUpdatedAt(LocalDateTime.now());
        if (session.getTitle() == null) {
            session.setTitle(generateTitleFromContent(content));
        }
        sessionRepo.save(session);
        
        return messageRepo.save(message);
    }
    
    /**
     * 获取会话的消息历史
     */
    public List<ChatMessage> getSessionMessages(Long sessionId, int limit) {
        if (limit > 0) {
            return messageRepo.findRecentBySession(sessionId, PageRequest.of(0, limit));
        }
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
    
    /**
     * 加载会话上下文（供 Agent 使用）
     */
    public String loadSessionContext(Long sessionId, int messageCount) {
        List<ChatMessage> messages = getSessionMessages(sessionId, messageCount);
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 归档会话
     */
    public void archiveSession(Long sessionId) {
        ChatSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setStatus(SessionStatus.ARCHIVED);
        sessionRepo.save(session);
    }
    
    private String generateDefaultTitle() {
        return "新对话 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
    
    private String generateTitleFromContent(String content) {
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }
}
```

**新建文件**: `backend/.../chat/service/UserMemoryService.java`

```java
@Service
@Transactional
public class UserMemoryService {
    
    private final UserMemoryRepository memoryRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 添加记忆
     */
    public UserMemory addMemory(Long userId, MemoryType type, String content, String source) {
        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setSource(source);
        memory.setImportance(1);
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        
        evictMemoryCache(userId, type);
        return memoryRepo.save(memory);
    }
    
    /**
     * 获取用户某类记忆
     */
    public List<UserMemory> getMemories(Long userId, MemoryType type) {
        String cacheKey = "memory:long:" + userId + ":" + type.name();
        List<UserMemory> cached = (List<UserMemory>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<UserMemory> memories = memoryRepo.findByUserIdAndMemoryType(userId, type);
        redisTemplate.opsForValue().set(cacheKey, memories, Duration.ofHours(1));
        return memories;
    }
    
    /**
     * 获取重要记忆（用于 Prompt 注入）
     */
    public String getImportantMemoriesForPrompt(Long userId, int limit) {
        List<UserMemory> memories = memoryRepo.findImportantMemories(userId, 3);
        if (memories.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("用户相关记忆：\n");
        memories.stream().limit(limit).forEach(m -> {
            sb.append("- ").append(m.getContent()).append("\n");
            m.setAccessCount(m.getAccessCount() + 1);
            m.setLastAccessedAt(LocalDateTime.now());
        });
        memoryRepo.saveAll(memories);
        
        return sb.toString();
    }
    
    /**
     * 从聊天中提取记忆（异步）
     */
    @Async
    public void extractMemoriesFromChat(Long userId, String userMessage, String agentResponse) {
        // 使用 LLM 分析对话，提取关键信息
        // 例如：用户说"我更喜欢用图表理解概念" -> 存储 PREFERENCE 记忆
    }
    
    /**
     * 搜索记忆
     */
    public List<UserMemory> searchMemories(Long userId, String keyword) {
        return memoryRepo.searchMemories(userId, keyword);
    }
    
    private void evictMemoryCache(Long userId, MemoryType type) {
        redisTemplate.delete("memory:long:" + userId + ":" + type.name());
    }
}
```

### 4.5 Controller 层

**新建文件**: `backend/.../chat/controller/ChatController.java`

```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatHistoryService chatHistoryService;
    
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getSessions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chatHistoryService.getUserSessions(userId, page, size));
    }
    
    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(
            @RequestParam Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        return ResponseEntity.ok(chatHistoryService.createSession(userId, title));
    }
    
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(chatHistoryService.getSessionMessages(sessionId, limit));
    }
    
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> archiveSession(@PathVariable Long sessionId) {
        chatHistoryService.archiveSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
```

**新建文件**: `backend/.../chat/controller/MemoryController.java`

```java
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {
    
    private final UserMemoryService memoryService;
    
    @GetMapping
    public ResponseEntity<List<UserMemory>> getMemories(
            @RequestParam Long userId,
            @RequestParam(required = false) MemoryType type) {
        if (type != null) {
            return ResponseEntity.ok(memoryService.getMemories(userId, type));
        }
        return ResponseEntity.ok(memoryService.searchMemories(userId, ""));
    }
    
    @PostMapping
    public ResponseEntity<UserMemory> addMemory(
            @RequestParam Long userId,
            @RequestBody UserMemory memory) {
        return ResponseEntity.ok(memoryService.addMemory(
            userId, memory.getMemoryType(), memory.getContent(), memory.getSource()));
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<UserMemory>> searchMemories(
            @RequestParam Long userId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(memoryService.searchMemories(userId, keyword));
    }
}
```

### 4.6 AgentMemory 改造

**修改文件**: `backend/.../agents/base/AgentMemory.java`

```java
@Component
public class AgentMemory {
    
    private final ChatHistoryService historyService;
    private final UserMemoryService memoryService;
    
    // 短期记忆（当前会话上下文，内存）
    private final Map<Long, List<MemoryEntry>> shortTermMemories = new ConcurrentHashMap<>();
    
    private static final int MAX_SHORT_TERM_SIZE = 20;
    
    /**
     * 记录消息（同时持久化）
     */
    public void addMessage(Long userId, Long sessionId, String role, String content, String agent) {
        // 1. 保存到短期记忆
        shortTermMemories.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new MemoryEntry(role, content, agent));
        trimMemory(userId);
        
        // 2. 持久化到数据库
        if (sessionId != null) {
            historyService.saveMessage(sessionId, userId, role, content, agent);
        }
        
        // 3. 如果是用户消息，尝试提取记忆
        if ("user".equals(role)) {
            memoryService.extractMemoriesFromChat(userId, content, null);
        }
    }
    
    /**
     * 获取上下文（短期记忆 + 重要长期记忆）
     */
    public String getContext(Long userId, Long sessionId) {
        StringBuilder context = new StringBuilder();
        
        // 1. 添加长期重要记忆
        String longTerm = memoryService.getImportantMemoriesForPrompt(userId, 3);
        if (!longTerm.isEmpty()) {
            context.append(longTerm).append("\n");
        }
        
        // 2. 添加短期对话历史
        String shortTerm = getFormattedHistory(userId, 10);
        if (!shortTerm.isEmpty()) {
            context.append(shortTerm);
        }
        
        return context.toString();
    }
    
    /**
     * 加载历史会话到短期记忆
     */
    public void loadSession(Long userId, Long sessionId) {
        List<ChatMessage> messages = historyService.getSessionMessages(sessionId, 20);
        List<MemoryEntry> entries = messages.stream()
                .map(m -> new MemoryEntry(m.getRole(), m.getContent(), m.getAgentName()))
                .collect(Collectors.toList());
        shortTermMemories.put(userId, entries);
    }
    
    // ... 保留现有的 getRecentHistory, getFormattedHistory, clearMemory 等方法
}
```

---

## 5. Phase 4: 前端增强

### 5.1 TypeScript 类型扩展

**修改文件**: `frontend/src/utils/types/chat.ts`

```typescript
// 新增会话类型
export interface ChatSession {
  id: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: Date
  updatedAt: Date
  messageCount?: number
  lastMessage?: string
}

// 扩展消息类型
export interface ChatMessage {
  id: string
  sessionId?: string
  role: ChatRole
  content: string
  contentType?: MessageContentType
  agentName?: string
  metadata?: Record<string, any>
  createdAt: Date
}
```

**新建文件**: `frontend/src/utils/types/memory.ts`

```typescript
export interface UserMemory {
  id: string
  memoryType: MemoryType
  content: string
  source: string
  importance: number
  createdAt: Date
}

export type MemoryType = 
  | 'PREFERENCE' 
  | 'LEARNING_PROGRESS' 
  | 'TOPIC_INTEREST' 
  | 'INTERACTION_STYLE' 
  | 'KNOWLEDGE_STATE'
```

**新建文件**: `frontend/src/utils/types/trace.ts`

```typescript
export interface TraceSpan {
  traceId: string
  spanId: string
  parentSpanId?: string
  agentId: string
  stepId?: string
  status: 'SUCCESS' | 'ERROR' | 'TIMEOUT'
  durationMs: number
  inputSummary?: string
  outputSummary?: string
  errorMessage?: string
  createdAt: Date
}

export interface TraceTimeline {
  traceId: string
  spans: TraceSpan[]
  totalDurationMs: number
}
```

### 5.2 Store 扩展

**修改文件**: `frontend/src/utils/store/chatStore.ts`

```typescript
interface ChatState {
  // 当前会话
  currentSessionId: string | null
  messages: ChatMessage[]
  
  // 会话列表
  sessions: ChatSession[]
  sessionsLoading: boolean
  
  // 操作
  createSession: () => Promise<string>
  loadSession: (sessionId: string) => Promise<void>
  loadSessions: () => Promise<void>
  addMessage: (msg: ChatMessage) => void
  appendToLastMessage: (chunk: string) => void
  
  // 现有方法保留
  setSessionId: (id: string) => void
  setConnected: (connected: boolean) => void
  clearMessages: () => void
}
```

**新建文件**: `frontend/src/utils/store/memoryStore.ts`

```typescript
interface MemoryState {
  memories: UserMemory[]
  loading: boolean
  
  loadMemories: (type?: MemoryType) => Promise<void>
  addMemory: (memory: Partial<UserMemory>) => Promise<void>
  searchMemories: (keyword: string) => Promise<void>
}
```

### 5.3 API 客户端

**新建文件**: `frontend/src/utils/api/chat.ts`

```typescript
import api from './client'

export const chatApi = {
  // 会话管理
  getSessions: (userId: string, page = 0, size = 20) => 
    api.get<ChatSession[]>(`/api/chat/sessions`, { params: { userId, page, size } }),
  
  createSession: (userId: string, title?: string) => 
    api.post<ChatSession>('/api/chat/sessions', { title }, { params: { userId } }),
  
  deleteSession: (sessionId: string) => 
    api.delete(`/api/chat/sessions/${sessionId}`),
  
  // 消息管理
  getMessages: (sessionId: string, limit = 50) => 
    api.get<ChatMessage[]>(`/api/chat/sessions/${sessionId}/messages`, { params: { limit } }),
  
  // 记忆管理
  getMemories: (userId: string, type?: MemoryType) => 
    api.get<UserMemory[]>('/api/memory', { params: { userId, type } }),
  
  addMemory: (userId: string, memory: Partial<UserMemory>) => 
    api.post<UserMemory>('/api/memory', memory, { params: { userId } }),
  
  searchMemories: (userId: string, keyword: string) => 
    api.get<UserMemory[]>('/api/memory/search', { params: { userId, keyword } }),
  
  // 链路追踪
  getTrace: (traceId: string) => 
    api.get<TraceTimeline>(`/api/trace/${traceId}`),
}
```

### 5.4 前端组件

```
frontend/src/components/
├── chat/
│   ├── ChatLayout.tsx            # 聊天页面主布局
│   ├── ChatSidebar.tsx           # 左侧边栏（会话列表）
│   ├── SessionList.tsx           # 会话列表
│   ├── SessionItem.tsx           # 单个会话项
│   ├── NewChatButton.tsx         # 新建聊天按钮
│   └── SessionSearch.tsx         # 会话搜索
│
├── memory/
│   ├── MemoryPanel.tsx           # 记忆面板（右侧）
│   ├── MemoryList.tsx            # 记忆列表
│   ├── MemoryItem.tsx            # 记忆项
│   └── MemoryEditor.tsx          # 记忆编辑
│
├── trace/
│   ├── TraceTimeline.tsx         # 追踪时间线
│   ├── TraceSpan.tsx             # 单个跨度
│   └── TraceDetail.tsx           # 追踪详情
│
└── layout/
    └── AppLayout.tsx             # 应用布局（整合侧边栏）
```

---

## 6. Phase 5: 测试与优化

### 6.1 后端测试

```java
// ChatHistoryServiceTest.java
@SpringBootTest
class ChatHistoryServiceTest {
    
    @Test
    void testCreateSession() {
        ChatSession session = chatHistoryService.createSession(1L, "测试会话");
        assertNotNull(session.getId());
        assertEquals("测试会话", session.getTitle());
    }
    
    @Test
    void testSaveAndRetrieveMessages() {
        ChatSession session = chatHistoryService.createSession(1L, null);
        chatHistoryService.saveMessage(session.getId(), 1L, "user", "你好", null);
        chatHistoryService.saveMessage(session.getId(), 1L, "assistant", "你好！有什么可以帮你？", "qa_agent");
        
        List<ChatMessage> messages = chatHistoryService.getSessionMessages(session.getId(), 10);
        assertEquals(2, messages.size());
    }
}

// UserMemoryServiceTest.java
@SpringBootTest
class UserMemoryServiceTest {
    
    @Test
    void testAddAndRetrieveMemory() {
        UserMemory memory = memoryService.addMemory(1L, MemoryType.PREFERENCE, "喜欢图表", "test");
        assertNotNull(memory.getId());
        
        List<UserMemory> memories = memoryService.getMemories(1L, MemoryType.PREFERENCE);
        assertFalse(memories.isEmpty());
    }
}

// PipelineEngineTest.java
@SpringBootTest
class PipelineEngineTest {
    
    @Test
    void testExecuteLearningPathPipeline() {
        PipelineConfig config = PipelineTemplates.learningPath();
        TaskContext context = new TaskContext("task-1", "user-1", "session-1", "学习 Python");
        
        PipelineResult result = pipelineEngine.execute(config, context);
        assertTrue(result.isSuccess());
    }
}
```

### 6.2 前端测试

```typescript
// chatStore.test.ts
describe('ChatStore', () => {
  it('should create a new session', async () => {
    const store = useChatStore.getState()
    const sessionId = await store.createSession()
    expect(sessionId).toBeDefined()
  })
  
  it('should load sessions list', async () => {
    const store = useChatStore.getState()
    await store.loadSessions()
    expect(store.sessions).toBeDefined()
  })
})
```

### 6.3 性能优化

| 优化项 | 方案 |
|--------|------|
| 消息分页 | 游标分页，避免深分页 |
| 记忆缓存 | Redis 缓存热点记忆，TTL 1小时 |
| 会话列表 | 虚拟滚动，只渲染可见项 |
| WebSocket | 心跳检测，断线重连 |
| 数据库 | 合理索引，避免全表扫描 |

---

## 7. 总体时间表

```
Week 1: 基础架构增强
├── Day 1-2: Phase 1 (Performative、AgentIds、TaskContext、AgentRunLog)
├── Day 3-4: Phase 2 (Pipeline 引擎核心)
└── Day 5: Pipeline 模板定义 + 测试

Week 2: 记忆系统
├── Day 1-2: 数据库表 + 实体类 + Repository
├── Day 3: Service 层实现
└── Day 4-5: Controller + AgentMemory 改造 + 测试

Week 3: 前端增强
├── Day 1-2: TypeScript 类型 + Store 重构
├── Day 3: API 客户端 + 聊天组件
├── Day 4: 记忆面板 + 链路追踪组件
└── Day 5: 集成测试 + 优化

总计: 约 3 周 (15 个工作日)
```

### 里程碑

| 里程碑 | 预计完成 | 交付物 |
|--------|----------|--------|
| M1: 架构增强 | Week 1 | Pipeline 引擎 + TaskContext |
| M2: 记忆系统 | Week 2 | 多用户记忆 + 聊天历史 |
| M3: 前端完善 | Week 3 | 完整 UI + 链路追踪 |

---

## 8. 技术栈确认

### 8.1 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.x | Web 框架 |
| Spring Data JPA | - | 数据访问 |
| MySQL | 8.x | 主数据库 |
| Redis | 7.x | 缓存 + 消息队列 |
| Lombok | - | 代码简化 |
| Jackson | - | JSON 处理 |

### 8.2 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 18.x | UI 框架 |
| TypeScript | 5.x | 类型系统 |
| Zustand | 4.x | 状态管理 |
| Tailwind CSS | 3.x | 样式 |
| Vite | 5.x | 构建工具 |

### 8.3 待确认项

- [ ] 是否已有 MySQL/Redis 环境？
- [ ] 用户认证方案（JWT? Session?）
- [ ] AI Server 记忆接口对接方式
- [ ] 部署环境（Docker? 直接部署?）

---

## 附录: 文件清单

### 新建文件

```
backend/src/main/java/com/lqragent/backend/
├── orchestrator/
│   ├── context/
│   │   └── TaskContext.java
│   └── pipeline/
│       ├── PipelineStep.java
│       ├── PipelineConfig.java
│       ├── PipelineEngine.java
│       ├── PipelineTemplates.java
│       └── PipelineResult.java
└── chat/
    ├── entity/
    │   ├── ChatSession.java
    │   ├── ChatMessage.java
    │   ├── UserMemory.java
    │   └── TraceSpan.java
    ├── repository/
    │   ├── ChatSessionRepository.java
    │   ├── ChatMessageRepository.java
    │   ├── UserMemoryRepository.java
    │   └── TraceSpanRepository.java
    ├── service/
    │   ├── ChatHistoryService.java
    │   └── UserMemoryService.java
    └── controller/
        ├── ChatController.java
        ├── MemoryController.java
        └── TraceController.java

frontend/src/
├── utils/
│   ├── types/
│   │   ├── memory.ts
│   │   └── trace.ts
│   ├── store/
│   │   └── memoryStore.ts
│   └── api/
│       └── chat.ts
└── components/
    ├── chat/
    │   ├── ChatLayout.tsx
    │   ├── ChatSidebar.tsx
    │   ├── SessionList.tsx
    │   ├── SessionItem.tsx
    │   ├── NewChatButton.tsx
    │   └── SessionSearch.tsx
    ├── memory/
    │   ├── MemoryPanel.tsx
    │   ├── MemoryList.tsx
    │   ├── MemoryItem.tsx
    │   └── MemoryEditor.tsx
    ├── trace/
    │   ├── TraceTimeline.tsx
    │   ├── TraceSpan.tsx
    │   └── TraceDetail.tsx
    └── layout/
        └── AppLayout.tsx
```

### 修改文件

```
backend/src/main/java/com/lqragent/backend/
├── orchestrator/
│   ├── AgentIds.java           # 新增 PROMPT_GEN, MEDIA_GEN
│   ├── OrchestratorCore.java   # 集成 PipelineEngine
│   └── message/
│       └── Performative.java   # 新增 NEGOTIATE, REFUSE, CFP 等
├── agents/base/
│   └── AgentMemory.java        # 重构支持持久化
└── chat/service/
    └── AgentRunLogService.java # 新增链路追踪字段

frontend/src/utils/
├── types/chat.ts               # 扩展 ChatSession, ChatMessage
└── store/chatStore.ts          # 重构支持多会话
```
