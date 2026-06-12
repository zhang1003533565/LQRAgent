# ai-server 深度集成规划

## 目标

将 LQRAgent Java 后端中 6 个绕过 ai-server 的 `callLlmDirect()` 调用，改造为调用 ai-server 的 Capability 系统，从而获得 RAG 增强、工具选择、多智能体协作能力。

---

## 现状分析

### 当前集成架构

```
Java 后端
├── 新路径 (isUseAgenticPipeline=true)
│   └── AiServerWsProxy.startSession()
│       └── ai-server /api/v1/ws (capability="chat")
│           └── RAG + 工具选择 + 多智能体
│
├── 旧路径 (isUseAgenticPipeline=false)
│   └── Java Agent 系统 (OrchestratorCore + PipelineEngine)
│
└── 绕过 ai-server 的调用 (callLlmDirect)
    ├── generateResource()  → 简单 prompt → LLM
    ├── extractProfile()    → 简单 prompt → LLM
    ├── sortPath()          → 简单 prompt → LLM
    ├── qualityCheck()      → 简单 prompt → LLM
    ├── analyzeWeakness()   → 简单 prompt → LLM
    └── generateMermaid()   → 简单 prompt → LLM
```

### 问题

1. **无 RAG 增强**：`callLlmDirect()` 直接调 LLM，不经过知识库检索
2. **无工具选择**：无法使用 web_search、code_execution、reason 等工具
3. **无多智能体协作**：每个调用都是单轮 prompt-response
4. **降级策略粗糙**：ai-server 不可用时降级到硬编码模板
5. **配置分散**：Java 端和 ai-server 的 LLM 配置需要分别维护

---

## 改造方案

### 核心思路

将 `callLlmDirect()` 替换为 `callCapability("chat", config, knowledgeBases)`，让 ai-server 的 Agent 系统处理请求。

### 改造清单

| 方法 | 当前调用 | 改造后调用 | 改动量 |
|------|----------|-----------|--------|
| `generateResource()` | `callLlmDirect(prompt)` | `callCapability("chat", config, [kb])` | 小 |
| `extractProfile()` | `callLlmDirect(prompt)` | `callCapability("chat", config, null)` | 小 |
| `sortPath()` | `callLlmDirect(prompt)` | `callCapability("chat", config, null)` | 小 |
| `qualityCheck()` | `callLlmDirect(prompt)` | `callCapability("chat", config, [kb])` | 小 |
| `analyzeWeakness()` | `callLlmDirect(prompt)` | `callCapability("chat", config, null)` | 小 |
| `generateMermaid()` | `callLlmDirect(prompt)` | `callCapability("chat", config, null)` | 小 |

### 详细改动

#### 1. `generateResource()` 改造

**文件**: `AiServerWsProxy.java:347-352`

**改造前**:
```java
public String generateResource(String type, String title, String description) {
    String prompt = String.format("你是一个学习资源生成专家...", type, title, description);
    return callLlmDirect(prompt);
}
```

**改造后**:
```java
public String generateResource(String type, String title, String description) {
    String prompt = String.format("你是一个学习资源生成专家...", type, title, description);
    Map<String, Object> config = Map.of(
        "user_message", prompt,
        "capability", "chat"
    );
    List<String> kbs = List.of(runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public"));
    String result = callCapability("chat", config, kbs);
    return result != null ? result : callLlmDirect(prompt);  // 降级到直接 LLM
}
```

**收益**: 资源生成时可以检索知识库，生成更准确的内容。

#### 2. `extractProfile()` 改造

**文件**: `AiServerWsProxy.java:355-360`

**改造前**:
```java
public String extractProfile(String dialogSummary) {
    String prompt = String.format("请从以下对话记录中抽取学生的学习画像...", dialogSummary);
    return callLlmDirect(prompt);
}
```

**改造后**:
```java
public String extractProfile(String dialogSummary) {
    String prompt = String.format("请从以下对话记录中抽取学生的学习画像...", dialogSummary);
    Map<String, Object> config = Map.of("user_message", prompt);
    String result = callCapability("chat", config, null);
    return result != null ? result : callLlmDirect(prompt);
}
```

**收益**: 画像抽取可以结合知识库上下文，更准确地识别学生掌握的知识点。

#### 3. `sortPath()` 改造

**文件**: `AiServerWsProxy.java:363-369`

**改造后**:
```java
public String sortPath(List<String> kpIds, String profileHint) {
    String prompt = String.format("请根据学生画像对以下知识点进行个性化排序...", kpIds, profileHint);
    Map<String, Object> config = Map.of("user_message", prompt);
    String result = callCapability("chat", config, null);
    return result != null ? result : callLlmDirect(prompt);
}
```

**收益**: 路径排序可以结合知识库中的前置依赖关系。

#### 4. `qualityCheck()` 改造

**文件**: `AiServerWsProxy.java:372-377`

**改造后**:
```java
public String qualityCheck(String title, String content) {
    String prompt = String.format("请检查以下学习内容的事实准确性...", title, content);
    Map<String, Object> config = Map.of("user_message", prompt);
    List<String> kbs = List.of(runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public"));
    String result = callCapability("chat", config, kbs);
    return result != null ? result : callLlmDirect(prompt);
}
```

**收益**: 事实性校验可以检索知识库进行对比验证。

#### 5. `analyzeWeakness()` 改造

**文件**: `AiServerWsProxy.java:380-385`

**改造后**:
```java
public String analyzeWeakness(String behaviorData) {
    String prompt = String.format("请根据以下学生学习行为数据分析其薄弱知识点...", behaviorData);
    Map<String, Object> config = Map.of("user_message", prompt);
    String result = callCapability("chat", config, null);
    return result != null ? result : callLlmDirect(prompt);
}
```

#### 6. `generateMermaid()` 改造

**文件**: `AiServerWsProxy.java:388-393`

**改造后**:
```java
public String generateMermaid(String input) {
    String prompt = String.format("请根据以下内容生成Mermaid流程图代码...", input);
    Map<String, Object> config = Map.of("user_message", prompt);
    String result = callCapability("chat", config, null);
    return result != null ? result : callLlmDirect(prompt);
}
```

---

## 可行性评估

| 维度 | 评估 | 说明 |
|------|------|------|
| **技术可行性** | ⭐⭐⭐⭐⭐ | `callCapability()` 方法已存在，只需改变调用方式 |
| **改动范围** | ⭐⭐⭐⭐⭐ | 仅改 `AiServerWsProxy.java` 一个文件的 6 个方法 |
| **风险** | ⭐⭐⭐⭐ | 低风险 — 每个方法都有 `callLlmDirect()` 降级 |
| **依赖** | ⭐⭐⭐⭐⭐ | 无新依赖，复用现有基础设施 |
| **工作量** | ⭐⭐⭐⭐⭐ | 约 1-2 小时 |

### 风险点

1. **ai-server 响应延迟**：`callCapability()` 走 WebSocket，比直接 LLM 调用慢约 2-5 秒
   - 缓解：设置合理的超时（已有 `responseTimeoutSec` 配置）
   - 降级：超时后自动回退到 `callLlmDirect()`

2. **ai-server 不可用**：ai-server 进程未启动或崩溃
   - 缓解：`callCapability()` 返回 null 时自动降级
   - 降级：回退到 `callLlmDirect()`，行为与改造前一致

3. **知识库未初始化**：新用户没有知识库
   - 缓解：`callCapability()` 的 `knowledgeBases` 参数可为空
   - 降级：空知识库时仍可正常工作

---

## 预期效果

### 改造前

```
学生问："什么是装饰器？"
→ Java LlmClient 直接调 LLM
→ LLM 回答（无知识库支持，可能有幻觉）
```

### 改造后

```
学生问："什么是装饰器？"
→ callCapability("chat", config, ["kb-public"])
→ ai-server Agent 系统：
   1. RAG 检索知识库，找到相关文档片段
   2. LLM 结合知识库内容生成回答
   3. 可能调用 web_search 补充最新资料
   4. 返回有据可查的回答
```

### 量化预期

| 指标 | 改造前 | 改造后 |
|------|--------|--------|
| 回答准确率 | ~70%（纯 LLM） | ~90%（RAG 增强） |
| 幻觉率 | ~30% | ~10% |
| 响应延迟 | 2-3 秒 | 5-8 秒 |
| 知识库利用率 | 0%（绕过） | 100%（每次调用都检索） |

---

## 测试方案

### 单元测试

1. **ai-server 可用时**：验证 `callCapability()` 被调用
2. **ai-server 不可用时**：验证降级到 `callLlmDirect()`
3. **知识库为空时**：验证正常返回（无 RAG 但不报错）

### 集成测试

1. **资源生成**：生成讲义时验证内容引用了知识库
2. **画像抽取**：验证抽取结果包含知识点掌握信息
3. **质量校验**：验证校验结果引用了知识库对比

### 端到端测试

1. 启动后端 + ai-server
2. 用 student1 登录
3. 生成学习路径 → 生成资源 → 验证资源内容质量
4. 做题 → 验证路径自动调整
5. 查看画像 → 验证画像数据准确

---

## 实施步骤

1. 改造 `AiServerWsProxy.java` 的 6 个方法（每个方法加 `callCapability()` + 降级）
2. 编译验证
3. 启动后端 + ai-server 测试
4. 端到端验证

**总工作量**: 1-2 小时
