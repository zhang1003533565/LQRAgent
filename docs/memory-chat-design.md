# 用户记忆 & 聊天历史管理 - 设计方案

## 1. 需求概述

### 1.1 多用户隔离记忆库
- **每个用户独立的记忆空间**：用户的记忆数据完全隔离
- **记忆持久化**：记忆存储到数据库/Redis，而非仅内存
- **记忆类型**：
  - **短期记忆**：当前会话的上下文（已实现，保留在内存）
  - **长期记忆**：跨会话的用户偏好、学习进度等（需新增）

### 1.2 聊天历史管理
- **聊天会话保存**：每次对话形成独立会话
- **历史会话列表**：用户可以查看所有历史会话
- **新建聊天**：用户可以创建新的会话
- **会话切换**：可以选择历史会话继续对话

### 1.3 聊天与记忆关联
- **记忆注入**：聊天时自动加载用户记忆上下文
- **记忆更新**：从聊天中提取关键信息更新记忆

---

## 2. 数据模型设计

### 2.1 数据库表结构

```sql
-- 聊天会话表
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),              -- 会话标题（自动生成或用户设置）
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
    role VARCHAR(20) NOT NULL,        -- user, assistant, system
    content TEXT NOT NULL,
    content_type VARCHAR(20) DEFAULT 'text',  -- text, multi_card, diagram
    agent_name VARCHAR(50),           -- 处理该消息的Agent
    metadata JSON,                    -- 附加信息（RAG源、图表代码等）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user_created (user_id, created_at)
);

-- 用户长期记忆表
CREATE TABLE user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_type VARCHAR(30) NOT NULL,  -- preference, learning_progress, topic_interest, interaction_style
    content TEXT NOT NULL,             -- 记忆内容
    source VARCHAR(50),               -- 来源（auto_extract, user_setting, agent_update）
    importance INT DEFAULT 1,         -- 重要程度 1-5
    access_count INT DEFAULT 0,       -- 访问次数
    last_accessed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, memory_type),
    INDEX idx_user_importance (user_id, importance DESC)
);
```

### 2.2 Redis 缓存设计

```
# 用户短期记忆（会话上下文）
memory:short:{userId} -> List<MemoryEntry>  (TTL: 24h)

# 用户长期记忆索引（热点数据）
memory:long:{userId}:{type} -> List<UserMemory>  (TTL: 1h)

# 当前会话ID
session:current:{userId} -> sessionId  (TTL: 7d)

# 会话消息缓存
session:messages:{sessionId} -> List<ChatMessage>  (TTL: 24h)
```

---

## 3. 后端实现设计

### 3.1 新增实体类

```java
// ChatSession.java
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

// ChatMessage.java  
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

// UserMemory.java
@Entity
@Table(name = "user_memory")
@Data
public class UserMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "memory_type")
    @Enumerated(EnumType.STRING)
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

// 枚举定义
public enum SessionStatus {
    ACTIVE, ARCHIVED, DELETED
}

public enum MemoryType {
    PREFERENCE,           // 用户偏好
    LEARNING_PROGRESS,    // 学习进度
    TOPIC_INTEREST,       // 话题兴趣
    INTERACTION_STYLE,    // 交互风格
    KNOWLEDGE_STATE       // 知识掌握状态
}
```

### 3.2 Repository 层

```java
// ChatSessionRepository.java
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, SessionStatus status);
    
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' ORDER BY s.updatedAt DESC")
    Page<ChatSession> findActiveSessions(Long userId, Pageable pageable);
}

// ChatMessageRepository.java
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentBySession(Long sessionId, Pageable pageable);
}

// UserMemoryRepository.java
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {
    List<UserMemory> findByUserIdAndMemoryType(Long userId, MemoryType type);
    
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.importance >= :minImportance ORDER BY m.importance DESC, m.lastAccessedAt DESC")
    List<UserMemory> findImportantMemories(Long userId, Integer minImportance);
    
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.content LIKE %:keyword%")
    List<UserMemory> searchMemories(Long userId, String keyword);
}
```

### 3.3 Service 层

```java
// ChatHistoryService.java
@Service
@Transactional
public class ChatHistoryService {
    
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final UserMemoryService memoryService;
    
    /**
     * 创建新会话
     */
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : generateDefaultTitle());
        session.setStatus(SessionStatus.ACTIVE);
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
    public ChatMessage saveMessage(Long sessionId, Long userId, String role, String content, String agentName) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setAgentName(agentName);
        message.setCreatedAt(LocalDateTime.now());
        
        // 同时更新会话的更新时间
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
     * 加载会话上下文（供Agent使用）
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
        // 取前20个字符作为标题
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }
}

// UserMemoryService.java
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
        
        // 清除缓存
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
     * 获取重要记忆（用于Prompt注入）
     */
    public String getImportantMemoriesForPrompt(Long userId, int limit) {
        List<UserMemory> memories = memoryRepo.findImportantMemories(userId, 3);
        if (memories.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("用户相关记忆：\n");
        memories.stream().limit(limit).forEach(m -> {
            sb.append("- ").append(m.getContent()).append("\n");
            // 更新访问计数
            m.setAccessCount(m.getAccessCount() + 1);
            m.setLastAccessedAt(LocalDateTime.now());
        });
        memoryRepo.saveAll(memories);
        
        return sb.toString();
    }
    
    /**
     * 从聊天中提取记忆（自动）
     */
    @Async
    public void extractMemoriesFromChat(Long userId, String userMessage, String agentResponse) {
        // 使用LLM分析对话，提取关键信息
        // 示例：用户说"我更喜欢用图表理解概念" -> 存储 PREFERENCE 记忆
        // 这里简化实现，实际需要调用LLM
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

---

## 4. 前端实现设计

### 4.1 新增类型定义

```typescript
// types/chat.ts - 扩展
export interface ChatSession {
  id: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: Date
  updatedAt: Date
  messageCount?: number
  lastMessage?: string
}

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

// types/memory.ts
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

### 4.2 Store 扩展

```typescript
// store/chatStore.ts - 重构
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
  
  // 其他现有方法...
}

// store/memoryStore.ts - 新增
interface MemoryState {
  memories: UserMemory[]
  loading: boolean
  
  loadMemories: (type?: MemoryType) => Promise<void>
  addMemory: (memory: Partial<UserMemory>) => Promise<void>
  searchMemories: (keyword: string) => Promise<void>
}
```

### 4.3 新增组件

```
components/
├── chat/
│   ├── ChatLayout.tsx            # 聊天页面布局
│   ├── ChatSidebar.tsx           # 侧边栏（会话列表）
│   ├── SessionList.tsx           # 会话列表组件
│   ├── SessionItem.tsx           # 单个会话项
│   ├── NewChatButton.tsx         # 新建聊天按钮
│   └── SessionSearch.tsx         # 会话搜索
│
├── memory/
│   ├── MemoryPanel.tsx           # 记忆面板
│   ├── MemoryList.tsx            # 记忆列表
│   ├── MemoryItem.tsx            # 记忆项
│   ├── MemoryEditor.tsx          # 记忆编辑器
│   └── MemorySearch.tsx          # 记忆搜索
│
└── layout/
    └── AppLayout.tsx             # 应用布局（整合侧边栏）
```

### 4.4 API 接口

```typescript
// api/chat.ts
export const chatApi = {
  // 会话管理
  getSessions: (userId: string) => 
    api.get<ChatSession[]>(`/api/chat/sessions`),
  
  createSession: (userId: string, title?: string) => 
    api.post<ChatSession>('/api/chat/sessions', { title }),
  
  getSession: (sessionId: string) => 
    api.get<ChatSession>(`/api/chat/sessions/${sessionId}`),
  
  deleteSession: (sessionId: string) => 
    api.delete(`/api/chat/sessions/${sessionId}`),
  
  // 消息管理
  getMessages: (sessionId: string, limit?: number) => 
    api.get<ChatMessage[]>(`/api/chat/sessions/${sessionId}/messages`, { params: { limit } }),
  
  // 记忆管理
  getMemories: (type?: MemoryType) => 
    api.get<UserMemory[]>('/api/memory', { params: { type } }),
  
  addMemory: (memory: Partial<UserMemory>) => 
    api.post<UserMemory>('/api/memory', memory),
  
  updateMemory: (memoryId: string, data: Partial<UserMemory>) => 
    api.put<UserMemory>(`/api/memory/${memoryId}`, data),
  
  deleteMemory: (memoryId: string) => 
    api.delete(`/api/memory/${memoryId}`),
  
  searchMemories: (keyword: string) => 
    api.get<UserMemory[]>('/api/memory/search', { params: { keyword } }),
}
```

---

## 5. 工作流程

### 5.1 新建聊天流程

```
用户点击"新建聊天"
    ↓
前端调用 createSession API
    ↓
后端创建 ChatSession 记录
    ↓
返回 sessionId
    ↓
前端设置 currentSessionId
    ↓
用户开始输入消息...
```

### 5.2 发送消息流程

```
用户发送消息
    ↓
前端保存消息到当前 sessionId
    ↓
后端 ChatHistoryService.saveMessage()
    ↓
加载用户记忆上下文
    ↓
UserMemoryService.getImportantMemoriesForPrompt()
    ↓
构建完整 Prompt（记忆 + 历史消息 + 用户输入）
    ↓
调用 LLM / Agent 处理
    ↓
保存 AI 响应到 ChatMessage
    ↓
异步提取记忆（如果需要）
    ↓
返回响应给前端
```

### 5.3 加载历史聊天流程

```
用户选择历史会话
    ↓
前端调用 loadSession(sessionId)
    ↓
后端返回会话信息 + 最近N条消息
    ↓
前端渲染消息列表
    ↓
用户可以继续对话...
```

---

## 6. 实现计划

### Phase 1: 后端基础设施 (1-2天)
- [ ] 创建数据库表（chat_session, chat_message, user_memory）
- [ ] 实现实体类和 Repository
- [ ] 实现 ChatHistoryService
- [ ] 实现 UserMemoryService

### Phase 2: 后端 API (1天)
- [ ] 创建 ChatController（REST API）
- [ ] 创建 MemoryController（REST API）
- [ ] 重构 WebSocket Handler 集成会话管理
- [ ] 集成记忆注入到 Agent 调用流程

### Phase 3: 前端基础 (1-2天)
- [ ] 扩展 TypeScript 类型定义
- [ ] 重构 chatStore 支持多会话
- [ ] 创建 memoryStore
- [ ] 实现 API 客户端

### Phase 4: 前端组件 (2-3天)
- [ ] 实现 ChatSidebar 组件
- [ ] 实现 SessionList 组件
- [ ] 重构 ChatLayout 支持会话切换
- [ ] 实现 MemoryPanel 组件
- [ ] 集成记忆显示到聊天界面

### Phase 5: 优化和测试 (1天)
- [ ] 实现会话搜索功能
- [ ] 实现记忆自动提取
- [ ] 性能优化（分页加载、缓存）
- [ ] 端到端测试

---

## 7. 与现有架构的集成

### 7.1 AgentMemory 改造

现有的 `AgentMemory` 类需要改造：

```java
@Component
public class AgentMemory {
    
    private final ChatHistoryService historyService;
    private final UserMemoryService memoryService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 短期记忆仍然保留在内存（当前会话上下文）
    private final Map<Long, List<MemoryEntry>> shortTermMemories = new ConcurrentHashMap<>();
    
    /**
     * 记录消息（同时持久化）
     */
    public void addMessage(Long userId, Long sessionId, String role, String content, String agent) {
        // 1. 保存到短期记忆
        shortTermMemories.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new MemoryEntry(role, content, agent));
        
        // 2. 持久化到数据库
        historyService.saveMessage(sessionId, userId, role, content, agent);
        
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
     * 加载历史会话
     */
    public void loadSession(Long userId, Long sessionId) {
        List<ChatMessage> messages = historyService.getSessionMessages(sessionId, 20);
        List<MemoryEntry> entries = messages.stream()
                .map(m -> new MemoryEntry(m.getRole(), m.getContent(), m.getAgentName()))
                .collect(Collectors.toList());
        shortTermMemories.put(userId, entries);
    }
}
```

### 7.2 OrchestratorCore 集成

```java
// 在 OrchestratorCore 中集成新的记忆系统
public ChatResponse processMessage(Long userId, Long sessionId, String message) {
    // 1. 加载上下文（记忆 + 历史）
    String context = agentMemory.getContext(userId, sessionId);
    
    // 2. 记录用户消息
    agentMemory.addMessage(userId, sessionId, "user", message, null);
    
    // 3. 调用 Agent 处理（带上下文）
    String response = processWithAgent(userId, message, context);
    
    // 4. 记录 Agent 响应
    agentMemory.addMessage(userId, sessionId, "assistant", response, "orchestrator");
    
    return new ChatResponse(response);
}
```

---

## 8. 关键设计决策

| 决策点 | 方案选择 | 理由 |
|--------|----------|------|
| 存储层 | MySQL + Redis | 结构化数据用MySQL，缓存用Redis |
| 记忆提取 | 异步 + LLM | 不阻塞主流程，LLM提取更准确 |
| 会话标题 | 自动生成 + 可编辑 | 首条消息自动生成，用户可修改 |
| 消息分页 | 倒序 + 游标 | 聊天场景下更自然 |
| 记忆重要性 | 访问频率 + 手动标记 | 自动学习 + 人工干预 |

---

## 9. 待确认问题

1. **用户认证**：当前是否有用户系统？还是单用户？
2. **数据迁移**：现有内存中的历史数据是否需要迁移？
3. **存储选型**：是否已有MySQL/Redis环境？
4. **AI Server 记忆**：与 AI Server 的记忆接口如何协调？

---

## 10. 总结

本方案实现了：
- ✅ **多用户隔离**：每个用户独立的记忆空间和会话列表
- ✅ **聊天历史持久化**：所有消息存储到数据库
- ✅ **历史会话管理**：查看、切换、新建会话
- ✅ **记忆系统**：短期记忆（会话）+ 长期记忆（跨会话）
- ✅ **记忆注入**：聊天时自动加载相关记忆

预计总工期：**6-8天**
