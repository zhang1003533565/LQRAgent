# 向量化 / RAG 完善方案

> 最后更新：2026-06-02
> 核心思路：Java 后端不自己实现向量化，全部调 ai-server 现成接口

---

## 当前问题总览

| # | 问题 | 影响 | 严重程度 |
|---|------|------|:--------:|
| 1 | Embedding 维度未自动填充，kb-public 可能处于 error | 公共知识库 RAG 不可用 | 高 |
| 2 | AiServerClient 只封装了 6 个方法（3 个是死代码） | 大量 ai-server 能力用不到 | 高 |
| 3 | RAG 的 sources（引用来源）被静默丢弃 | 前端看不到"回答基于哪段教材" | 中 |
| 4 | ContentAnalyzer 用子串匹配关联知识点，PDF 读出来是乱码 | 上传文档的知识点映射基本失效 | 中 |
| 5 | 上传文件后不等向量化完成就标记 COMPLETED | 用户以为能用但实际还在处理 | 中 |
| 6 | StreamCallback 接口只有 3 个方法，无法传递结构化数据 | 无法扩展 sources 等事件 | 低 |

---

## 改动计划（6 步，按优先级排序）

### Step 1：修复 Embedding 配置 + 验证连通性

**改什么：** 不需要改代码，纯运维操作 + 加一个验证接口。

**操作步骤：**
1. 在管理后台执行一次"测试 Embedding 连接"（或手动调 `POST /api/v1/system/test/embeddings`）
2. ai-server 会自动把 `bge-large-zh-v1.5` 的实际维度 **1024** 填充到 `model_catalog.json`
3. 然后调 `POST /api/v1/knowledge/kb-public/reindex` 重建公共知识库索引
4. 调 `GET /api/v1/knowledge/kb-public/progress` 确认索引重建完成

**在 AiServerClient 中加两个方法：**
```java
// 文件: chat/proxy/AiServerClient.java

/** 测试 Embedding 连通性 */
public boolean testEmbedding() {
    try {
        var resp = client().post()
            .uri("/api/v1/system/test/embeddings")
            .retrieve()
            .toBodilessEntity();
        return resp.getStatusCode().is2xxSuccessful();
    } catch (Exception e) {
        log.warn("[AiServer] embedding test failed: {}", e.getMessage());
        return false;
    }
}

/** 重建知识库索引（换 Embedding 模型后需要） */
public boolean reindex(String kbName) {
    try {
        var resp = client().post()
            .uri("/api/v1/knowledge/{kbName}/reindex", kbName)
            .retrieve()
            .toBodilessEntity();
        return resp.getStatusCode().is2xxSuccessful();
    } catch (Exception e) {
        log.warn("[AiServer] reindex failed for {}: {}", kbName, e.getMessage());
        return false;
    }
}
```

**验证：** 管理后台保存模型配置后自动调 `testEmbedding()`，成功才提示配置生效。

---

### Step 2：扩展 AiServerClient（补 5 个方法）

**改什么：** `chat/proxy/AiServerClient.java` 新增方法。

```java
/** RAG 检索 — 在知识库中搜索最相关的文档片段 */
public List<Map<String, Object>> searchKnowledgeBase(String kbName, String query, int topK) {
    try {
        Map<String, Object> body = Map.of("query", query, "top_k", topK);
        return client().post()
            .uri("/api/v1/knowledge/{kbName}/search", kbName)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(List.class);
    } catch (Exception e) {
        log.warn("[AiServer] KB search failed: {}", e.getMessage());
        return List.of();
    }
}

/** 查看知识库中的文件列表 */
public List<Map<String, Object>> getKnowledgeBaseFiles(String kbName) {
    try {
        return client().get()
            .uri("/api/v1/knowledge/{kbName}/files", kbName)
            .retrieve()
            .body(List.class);
    } catch (Exception e) {
        log.warn("[AiServer] list KB files failed: {}", e.getMessage());
        return List.of();
    }
}

/** 查看知识库向量化处理进度 */
public Map<String, Object> getProgress(String kbName) {
    try {
        return client().get()
            .uri("/api/v1/knowledge/{kbName}/progress", kbName)
            .retrieve()
            .body(Map.class);
    } catch (Exception e) {
        log.warn("[AiServer] get progress failed: {}", e.getMessage());
        return Map.of();
    }
}

/** 获取系统完整状态（LLM/Embedding/Search 连通性） */
public Map<String, Object> getSystemStatus() {
    try {
        return client().get()
            .uri("/api/v1/system/status")
            .retrieve()
            .body(Map.class);
    } catch (Exception e) {
        log.warn("[AiServer] system status failed: {}", e.getMessage());
        return Map.of();
    }
}

/** 删除知识库中的文档（目前 ai-server 支持删整个 KB，单文件删除待确认） */
public boolean deleteKnowledgeBase(String kbName) {
    try {
        client().delete()
            .uri("/api/v1/knowledge/{kbName}", kbName)
            .retrieve()
            .toBodilessEntity();
        return true;
    } catch (Exception e) {
        log.warn("[AiServer] delete KB failed: {}", e.getMessage());
        return false;
    }
}
```

**同时修复现有问题：**
- `createKnowledgeBase()` 和 `uploadDocument()` 中的 `new RestTemplate()` 替换为带超时的配置（或用 `RestClient` 重写 multipart 上传）
- 上传超时从默认值改为 connect=5s, read=120s（大文件需要更长时间）

---

### Step 3：透传 RAG 引用来源给前端

**改什么：** 两个文件。

**3a. 扩展 StreamCallback 接口（AiServerWsProxy.java 第 180-184 行）：**
```java
// 原来的接口
public interface StreamCallback {
    void onChunk(String content);
    void onDone(String result);
    void onError(String error);
}

// 改为（加一个 default 方法，不破坏现有调用方）
public interface StreamCallback {
    void onChunk(String content);
    void onDone(String result);
    void onError(String error);
    default void onSources(List<Map<String, Object>> sources) {}  // 新增
}
```

**3b. 修改事件处理（AiServerWsProxy.java 第 124-128 行）：**
```java
// 原来的 default 分支（全部丢弃）
default -> {
    log.debug("[AiServerWsProxy] skipping event type: {}", type);
}

// 改为（把 sources 提取出来回调）
case "sources" -> {
    try {
        JsonNode sourcesNode = payload.get("sources");
        if (sourcesNode != null && sourcesNode.isArray()) {
            List<Map<String, Object>> sources = objectMapper
                .convertValue(sourcesNode, new TypeReference<>() {});
            callback.onSources(sources);
        }
    } catch (Exception e) {
        log.warn("[AiServerWsProxy] parse sources error: {}", e.getMessage());
    }
}
default -> {
    log.debug("[AiServerWsProxy] skipping event type: {}", type);
}
```

**3c. 在 ChatWebSocketHandler 中把 sources 推给前端：**
在 QA 回调处实现 `onSources`，通过 WebSocket 推送 `artifact` 事件：
```java
@Override
public void onSources(List<Map<String, Object>> sources) {
    // 推送给前端，前端可以显示"本回答参考了以下资料"
    pushEvent(session, Map.of(
        "type", "artifact",
        "kind", "rag_sources",
        "payload", sources
    ));
}
```

---

### Step 4：改造 ContentAnalyzer 用 RAG 检索替代子串匹配

**改什么：** `ContentAnalyzerService.java` 的两个方法。

**4a. 修复 PDF 读取（readFileContent 方法）：**

引入 Apache PDFBox 依赖（pom.xml）：
```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```

修改 readFileContent：
```java
private String readFileContent(String objectKey, String fileName) {
    try {
        byte[] bytes = qiniuStorageService.download(objectKey);
        if (fileName.toLowerCase().endsWith(".pdf")) {
            // PDF 用 PDFBox 提取文本
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(doc);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
        log.warn("[ContentAnalyzer] read error: {}", e.getMessage());
        return null;
    }
}
```

**4b. 用 RAG 检索替代子串匹配（matchKnowledgePoints 方法）：**

```java
private List<String> matchKnowledgePoints(String content) {
    // 方案：把文档内容当作 query，在公共知识库中做 RAG 检索
    // 返回的 sources 里包含最相关的文档片段 → 从中提取知识点 ID
    String truncated = content.length() > 2000 ? content.substring(0, 2000) : content;
    List<Map<String, Object>> results = aiServerClient.searchKnowledgeBase(
        "kb-public", truncated, 5
    );

    // 从检索结果中提取关联的知识点
    // （前提：上传到知识库的教材文档本身包含知识点标题信息）
    // 如果检索结果不够精确，仍用 LLM 分析兜底
    if (results.isEmpty()) {
        return matchBySubstring(content);  // 保留子串匹配作为降级
    }

    // 提取匹配到的文本片段，再用 LLM 或规则映射到知识点 ID
    String retrievedText = results.stream()
        .map(r -> String.valueOf(r.getOrDefault("text", "")))
        .collect(Collectors.joining("\n"));
    return mapTextToKnowledgePoints(retrievedText);
}

/** 从检索文本映射到知识点 ID（规则匹配，比全量子串匹配精准） */
private List<String> mapTextToKnowledgePoints(String text) {
    List<KnowledgePoint> allKps = knowledgePointRepo.findAll();
    String lower = text.toLowerCase();
    return allKps.stream()
        .filter(kp -> lower.contains(kp.getTitle().toLowerCase()))
        .map(KnowledgePoint::getKpId)
        .collect(Collectors.toList());
}
```

---

### Step 5：上传后追踪向量化进度

**改什么：** `UploadQueueService.java` 的 `processTask()` 方法。

**原来的流程：**
```
下载文件 → 上传到 ai-server → 内容分析 → 标记 COMPLETED
```

**改为：**
```
下载文件 → 上传到 ai-server → 标记 PROCESSING
→ 轮询 getProgress(kbName) 等待向量化完成（最多等 3 分钟）
→ 内容分析 → 标记 COMPLETED
```

关键改动位置在 processTask() 第 168-179 行：
```java
// 上传成功后，等待向量化完成
if (uploadSuccess) {
    task.setStatus(TaskStatus.PROCESSING);
    uploadTaskRepo.save(task);

    // 轮询进度，最多等 180 秒，每 5 秒查一次
    boolean indexed = false;
    for (int i = 0; i < 36; i++) {
        Map<String, Object> progress = aiServerClient.getProgress(kbName);
        String status = String.valueOf(progress.getOrDefault("status", ""));
        if ("ready".equals(status) || "idle".equals(status) || progress.isEmpty()) {
            indexed = true;
            break;
        }
        if ("error".equals(status)) {
            log.error("[Upload] KB indexing error: {}", progress);
            break;
        }
        Thread.sleep(5000);
    }

    if (!indexed) {
        log.warn("[Upload] KB indexing timeout for {}, proceeding anyway", kbName);
    }
}

// 内容分析（现在 PDF 也能正确解析了）
AnalysisResult analysis = contentAnalyzerService.analyze(
    task.getFilePath(), task.getFileName()
);
task.setAnalysisResult(analysis.toJson());
task.setMappedKpIds(analysis.getMappedKpIds());
task.setStatus(uploadSuccess ? TaskStatus.COMPLETED : TaskStatus.FAILED);
```

---

### Step 6：管理后台知识库管理页面（可选，时间充裕再做）

**改什么：** 管理后台加一个简单的知识库面板。

**数据来源：** 直接调 Step 2 新增的 AiServerClient 方法。

**展示内容：**
- 知识库列表（名称、文件数、状态）→ `listKnowledgeBases()`
- 每个知识库的文件列表 → `getKnowledgeBaseFiles(kbName)`
- 重建索引按钮 → `reindex(kbName)`
- 系统状态（LLM/Embedding 连通性）→ `getSystemStatus()`

---

## 改动文件清单

| 步骤 | 文件 | 改动类型 |
|------|------|---------|
| 1 | `chat/proxy/AiServerClient.java` | 加 testEmbedding() + reindex() |
| 2 | `chat/proxy/AiServerClient.java` | 加 5 个方法 + 修复 RestTemplate 超时 |
| 3a | `chat/proxy/AiServerWsProxy.java` | 扩展 StreamCallback 接口 |
| 3b | `chat/proxy/AiServerWsProxy.java` | 改 default 分支处理 sources |
| 3c | `chat/handler/ChatWebSocketHandler.java` | 实现 onSources 推送前端 |
| 4a | `agents/contentanalyzer/service/ContentAnalyzerService.java` | 加 PDFBox 解析 |
| 4a | `backend/pom.xml` | 加 PDFBox 依赖 |
| 4b | `agents/contentanalyzer/service/ContentAnalyzerService.java` | RAG 检索替代子串匹配 |
| 5 | `uploadqueue/service/UploadQueueService.java` | processTask 加进度轮询 |
| 6 | 管理后台页面（新增） | 可选 |

## 预估工时

| 步骤 | 预估 | 说明 |
|------|------|------|
| Step 1 | 0.5 天 | 运维操作 + 2 个简单方法 |
| Step 2 | 0.5 天 | 5 个 HTTP 方法封装 + 超时修复 |
| Step 3 | 1 天 | 接口扩展 + WS 事件透传 + 前端渲染 |
| Step 4 | 1 天 | PDFBox 集成 + RAG 检索改造 |
| Step 5 | 0.5 天 | 进度轮询逻辑 |
| Step 6 | 1 天 | 管理后台 UI（可选） |
| **合计** | **3.5-4.5 天** | 不含可选的管理后台 |
