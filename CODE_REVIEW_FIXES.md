# ContentOps AI 代码审查修复清单

> 审查范围: Spring Boot 3.2 + Spring AI 1.0.0 GA 项目核心模块
> 审查重点: Spring AI API 兼容性、线程安全、异常处理、资源管理、SQL 注入、日志规范、空指针防护、代码重复

---

## 一、capability/retrieval/ 检索模块

### 1. QdrantVectorStoreService.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 1 | Spring AI 1.0.0 GA API 兼容性 | `EmbeddingModel.embed()` 返回类型从 `List<Double>` 变为 `float[]`, 原代码使用了错误的返回类型, 编译失败 | 将 `embeddingModel.embed(query)` 返回值从 `List<Double>` 改为 `float[]`, 新增 `toFloatList()` 方法将 `float[]` 转为 `List<Float>` 供 Qdrant gRPC API 使用 |
| 2 | 资源管理 - 缺少超时 | Qdrant gRPC 调用 `searchAsync().get()` 无超时, 网络异常时会无限阻塞检索线程 | 新增 `SEARCH_TIMEOUT_SECONDS=10` 和 `WRITE_TIMEOUT_SECONDS=15` 常量, 所有 `.get()` 调用增加超时参数 |
| 3 | 异常处理 - InterruptedException 吞没 | `catch (Exception e)` 捕获 InterruptedException 后未恢复中断标志, 导致线程池无法正确终止 | 在所有 catch 块中增加 `if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }` |
| 4 | 空指针防护 | `embed()` 返回值可能为 null 或空数组, 直接使用会导致 NPE | 新增 null 和空数组检查, 返回 `Collections.emptyList()` |
| 5 | 空指针防护 | `searchAsync().get()` 返回值可能为 null | 新增 null 检查 |
| 6 | 异常处理 - 静默吞异常 | 原代码异常未记录日志或仅记录消息不带堆栈 | 所有 catch 块增加 `log.warn(..., e)` 带完整堆栈 |

### 2. HybridRetriever.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 7 | 线程安全 - 误用 ForkJoinPool | `CompletableFuture.supplyAsync` 默认使用 `ForkJoinPool.commonPool`, 检索为阻塞 I/O 操作, 会饿死 CPU 密集任务 | 新增专用线程池 `retrievalExecutor` (8 线程 FixedThreadPool), 使用自定义 `RetrievalThreadFactory` 命名线程便于排查 |
| 8 | 线程安全 - 缺少超时 | `CompletableFuture.allOf().get()` 无超时, 单路检索阻塞会拖垮整体流程 | 新增 `PARALLEL_TIMEOUT_SECONDS=15`, 超时后取消未完成 future |
| 9 | 异常处理 - InterruptedException 吞没 | 并行等待超时后未恢复中断标志 | 增加 `Thread.currentThread().interrupt()` |
| 10 | 资源管理 - 线程泄漏 | 超时后未取消 future, 阻塞线程持续占用资源 | 增加 `vectorFuture.cancel(true)` 和 `bm25Future.cancel(true)` |
| 11 | 异常处理 - 降级逻辑缺陷 | 原代码超时后串行重试, 放大下游压力 | 改为 `safeJoin()` 尝试取已就绪结果, 失败用空列表兜底, 不再串行重试 |
| 12 | 资源管理 - 线程池未关闭 | 线程池缺少生命周期管理, 应用关闭时线程泄漏 | 新增 `@PreDestroy shutdown()` 方法, 优雅关闭线程池 (5 秒等待 + shutdownNow 兜底) |
| 13 | 异常处理 - 单路检索失败影响全局 | 向量/BM25 检索单路异常会导致整个流程中断 | 抽取 `safeVectorSearch` 和 `safeBm25Search` 方法, 单路失败返回空列表不影响另一路 |

### 3. BM25Service.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 14 | **SQL 注入** | `tsConfig` 通过字符串拼接构造 SQL (`to_tsquery('` + tsConfig + `', ?)`), 攻击者可通过配置注入恶意 SQL | 改为参数化查询 `?::regconfig`, 同时新增 `ALLOWED_TS_CONFIGS` 白名单做双重防护 |
| 15 | 空指针防护 | `query` 为 null/空时直接执行 SQL 浪费资源 | 方法入口增加 `query == null || query.isBlank()` 检查 |
| 16 | 异常处理 - tenantId 类型不安全 | `tenantId` 直接作为参数传入 SQL, 字符串类型不匹配 Long 字段 | 增加 `Long.parseLong(tenantId)` 转换, NumberFormatException 时返回空列表 |
| 17 | 异常处理 - 静默吞异常 | 外部 DB 调用无 try-catch, 异常直接抛出中断调用方 | 增加 try-catch, 异常时返回空列表并记录 warn 日志 |

### 4. CrossEncoderReranker.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 18 | 资源管理 - RestTemplate 无超时 | 原代码使用默认 RestTemplate, 无连接/读取超时, rerank 服务不可用时会无限阻塞 | 改用 `RestTemplateBuilder` 构造, 设置 5 秒连接超时 + 10 秒读取超时 |
| 19 | 空指针防护 | `documents` 参数未做 null 检查 | 增加 null 和 empty 检查 |
| 20 | 空指针防护 | `topK <= 0` 时 `stream().limit()` 抛 IllegalArgumentException | 新增 `safeTopK = Math.max(0, topK)` 防御性归一化 |
| 21 | 空指针防护 | 文档 content 为 null 时发送给 rerank 服务导致 NPE | 增加 content null/blank 过滤 |
| 22 | 空指针防护 | `Map.of("query", query, ...)` 中 query 为 null 时抛 NPE | 增加 `query == null ? "" : query` 防御 |
| 23 | 异常处理 - 降级逻辑 | rerank 服务异常时无降级处理, 中断检索流程 | 增加 try-catch, 异常时调用 `fallback()` 返回原始前 topK 结果 |
| 24 | 异常处理 - 响应校验 | 未校验 rerank 返回的 scores 数量与文档数量是否匹配 | 增加响应校验, 不匹配时降级 |

### 5. RRFFusionStrategy.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 25 | 空指针防护 | `k <= 0` 时 RRF 公式分母为 0 或负数, 计算异常 | 增加 `safeK = k <= 0 ? DEFAULT_K : k` |
| 26 | 空指针防护 | `results` 参数可能为 null | 增加 null 检查 |
| 27 | 空指针防护 | `doc.getId()` 为 null 时作为 Map key 导致异常 | 增加 `doc.getId() == null` 跳过检查 |
| 28 | 日志规范 | 缺少调试日志, 无法排查融合结果 | 增加 `log.debug` 记录融合数量 |

---

## 二、capability/fallback/ 降级链模块

### 6. ModelFallbackChain.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 29 | 线程安全 - 端点列表懒加载竞态 | 多线程同时首次调用 `endpoints()` 会重复初始化排序 | 使用 double-checked locking (`volatile` + `synchronized`) 保证只初始化一次 |
| 30 | 异常处理 - 重试逻辑缺陷 | 响应体解析失败 (确定性错误) 也被重试, 浪费配额 | 新增 `ModelResponseException` 区分确定性错误, 该异常不重试直接抛出 |
| 31 | 资源管理 - RestTemplate 无独立超时 | 所有端点共享同一 RestTemplate, 无法按端点配置不同超时 | 新增 `endpointRestTemplates` ConcurrentMap, 按端点构建带独立超时的 RestTemplate 并缓存复用 |
| 32 | 异常处理 - 降级链元信息缺失 | `chat()` 方法仅返回文本, 无法区分正常模型返回/缓存命中/模板兜底 | 新增 `ChatResult` record 和 `chatWithMeta()` 方法, 携带 modelUsed/degraded/source 元信息 |
| 33 | 空指针防护 | `prompt` 为 null 时传入模型请求导致异常 | 增加 `prompt == null ? "" : prompt` 防御 |
| 34 | 异常处理 - 语义缓存兜底无 try-catch | 缓存服务异常会中断降级链 | 增加独立 try-catch, 缓存异常时直接走模板兜底 |
| 35 | 空指针防护 - 配置缺失 | 端点配置 Bean 缺失时 NPE | 增加 `getIfAvailable()` null 检查, 缺失时使用安全兜底端点 |

### 7. CircuitBreakerManager.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 36 | 线程安全 - 熔断器注册竞态 | `breakers.get()` + `register()` 非原子操作, 可能重复注册 | 改用 `breakers.computeIfAbsent(name, this::register)` 原子操作 |
| 37 | 日志规范 | 熔断器注册无日志, 无法排查 | 增加 `log.info("已注册模型熔断器: {}", name)` |

---

## 三、capability/cache/ 语义缓存模块

### 8. SemanticCacheService.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 38 | Spring AI 1.0.0 GA API 兼容性 | `EmbeddingModel.embed()` 返回 `List<Double>`, 与 GA 版 `float[]` 不兼容 | 改为 `float[]` 返回类型, 新增 null/空数组检查 |
| 39 | 空指针防护 | `query` 为 null/空时执行向量化和检索浪费资源 | `get()` 和 `put()` 入口增加 blank 检查 |
| 40 | 空指针防护 | `answer` 为 null 时写入缓存无意义 | 增加 null 检查 |
| 41 | 空指针防护 | payload 中 `expires_at` 可能是 Number 或 String, 直接强转异常 | 新增 `toEpochMillis()` 方法统一处理 Number/String 类型 |
| 42 | 空指针防护 | `top.payload()` 可能返回 null | 增加 null 检查 |
| 43 | 异常处理 - 静默吞异常 | 缓存读写异常未记录日志 | 所有 catch 块增加 `log.warn(..., e)` |
| 44 | 空指针防护 | `Map.of()` 不允许 null value, `tenantId` 为 null 时 NPE | 增加 `tenantId == null ? "" : tenantId` |

---

## 四、capability/evaluation/ 评估模块

### 9. RagasEvaluator.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 45 | 异常处理 - 评估子流程无容错 | 单个子评估器异常会导致整个 RAGAS 评估中断 | 三个子评估器分别独立 try-catch, 失败维度记为 0.0 |
| 46 | 异常处理 - 持久化无容错 | 评估结果持久化失败会中断返回 | 持久化独立 try-catch, 失败仅记录 warn 日志 |
| 47 | 空指针防护 | `contexts` 为 null 时序列化 NPE | 增加 `contexts == null ? List.of() : contexts` |
| 48 | 日志规范 | 缺少评估开始/完成日志 | 增加 `log.info` 记录评估开始和各维度完成结果 |

### 10. FaithfulnessEvaluator.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 49 | **安全漏洞 - String.format 注入** | 使用 `String.format(prompt, answer)` 拼接 prompt, answer 中的 `%` 字符会触发 `IllegalFormatConversionException` | 改为字符串拼接 `EXTRACT_PROMPT + answer` |
| 50 | 资源管理 - ChatClient 重复创建 | 每次评估调用 `chatClientBuilder.build()` 创建新实例, 对象创建开销大 | 在 `@PostConstruct init()` 中创建并复用单一 ChatClient 实例 |
| 51 | 异常处理 - LLM 调用无 try-catch | `chatClient.prompt().call()` 异常会中断评估 | 抽取 `extractClaims()` 和 `isSupported()` 方法, 独立 try-catch |
| 52 | 空指针防护 | `answer`/`contexts` 为 null/空时执行评估无意义 | 入口增加 null/blank 检查 |

### 11. AnswerRelevancyEvaluator.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 53 | Spring AI 1.0.0 GA API 兼容性 | `EmbeddingModel.embed(List<String>)` 返回类型变化, 原代码使用 `List<List<Double>>` | 改为 `float[][]` 返回类型, 适配 GA API |
| 54 | 性能 - N+1 向量化调用 | 原代码对 query 和每个生成问题分别调用 `embed()`, N+1 次网络调用 | 改为批量 `embed(List<String>)` 一次性向量化, 减少 N-1 次网络调用 |
| 55 | 资源管理 - ChatClient 重复创建 | 同 FaithfulnessEvaluator | 在 `@PostConstruct init()` 中创建并复用 |
| 56 | **安全漏洞 - String.format 注入** | `String.format(prompt, answer)` 同上问题 | 改为字符串拼接 |
| 57 | 空指针防护 | embedding 返回值可能为 null 或空数组 | 增加 null 和空数组检查 |
| 58 | 异常处理 - 静默吞异常 | 评估异常无日志 | 增加 `log.warn(..., e)` |

### 12. ContextPrecisionEvaluator.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 59 | 资源管理 - ChatClient 重复创建 | 同上 | 在 `@PostConstruct init()` 中创建并复用 |
| 60 | **安全漏洞 - String.format 注入** | `String.format(prompt, query, context)` 同上问题 | 改为字符串拼接 |
| 61 | 异常处理 - LLM 调用无 try-catch | 同上 | 抽取 `isUseful()` 方法, 独立 try-catch |

---

## 五、capability/validation/ 校验模块

### 13. StructuredOutputGuard.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 62 | 资源管理 - ChatClient 重复创建 | 每次修复调用都 build 新 ChatClient | 在 `@PostConstruct init()` 中创建并复用 |
| 63 | **安全漏洞 - String.format 注入** | `String.format(prompt, schemaText, errorText, original)` 中 original 含 `%` 时异常 | 改为字符串拼接 |
| 64 | 异常处理 - JSON 解析无容错 | LLM 输出非标准 JSON 时直接抛异常中断流程 | 新增 `parseJsonLenient()` 方法, 先尝试直接解析, 失败后正则提取 JSON 片段重试 |
| 65 | 空指针防护 | `llmOutput` 为 null/空时解析 NPE | 增加 null/blank 检查 |
| 66 | 异常处理 - 修复流程无 try-catch | LLM 修复调用异常中断整个校验 | `validateAndRepair()` 中增加 try-catch, 包装为 `StructuredOutputException` |

---

## 六、agent/ Agent 编排模块

### 14. BaseAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 67 | **线程安全 - ThreadLocal 泄漏** | `MODEL_USED`/`TOKENS_USED`/`RAG_CONTEXTS`/`ANSWER_TEXT` 四个 ThreadLocal 在 finally 中未清理, 线程复用导致数据串号 | finally 块中增加所有 ThreadLocal 的 `remove()` 调用 |
| 68 | **缓存污染 - 模板兜底写入缓存** | 模型全部降级到模板兜底文案时, 兜底文案被写入语义缓存, 后续命中返回无意义内容 | 新增 `CACHEABLE` ThreadLocal, 子类可调用 `setCacheable(false)` 标记不可缓存, 写缓存前检查 |
| 69 | 异常处理 - 执行记录未记录失败 | Agent 执行异常时未更新执行记录状态 | catch 块中调用 `executionService.completeExecution(..., FAILED, ...)` |
| 70 | 空指针防护 - ThreadLocal 取值 | `TOKENS_USED.get()` 可能为 null, 直接拆箱 NPE | 增加 null 检查 `TOKENS_USED.get() != null ? TOKENS_USED.get() : estimatedTokens` |
| 71 | 日志规范 - 缺少上下文 | 日志缺少 traceId, 无法链路追踪 | 所有关键日志增加 traceId |
| 72 | 空指针防护 - 序列化失败 | `objectMapper.writeValueAsString(result)` 可能抛异常 | 新增 `serializeSafely()` 和 `deserializeSafely()` 方法, 失败时回退 toString/原样返回 |
| 73 | 空指针防护 - buildOutput | `objectMapper.convertValue()` 可能抛异常 | 增加 try-catch, 失败时回退 `Map.of("result", String.valueOf(result))` |
| 74 | 设计缺陷 - 副作用 Agent 缓存 | 发布类 Agent 命中缓存会跳过真实发布, 返回历史 postId | 新增 `supportsSemanticCache()` 钩子方法, 默认 true, 有副作用的 Agent 覆盖为 false |

### 15. ContentCreationAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 75 | 空指针防护 | `Document.getContent()` 可能为 null, 收集 contexts 时 NPE | 增加 `filter(Objects::nonNull)` |
| 76 | 代码重复 | prompt 构建中截断逻辑与 BaseAgent 重复 | 复用 BaseAgent 的 `truncate()` 方法 |

### 16. PublishAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 77 | **缓存污染 - 副作用操作缓存** | 发布动作有副作用, 命中缓存会跳过发布返回历史 postId | 覆盖 `supportsSemanticCache()` 返回 false |

### 17. DataAnalysisAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 78 | **空指针防护 - 日期参数类型不安全** | `params.get("startDate")` 可能返回非 LocalDate 类型, 直接强转 ClassCastException | 新增 `resolveDate()` 方法, 使用 `instanceof` 类型检查, 不符时回退默认值 |
| 79 | 缓存污染 - 模板兜底写入缓存 | 模型降级到模板兜底时写入无意义缓存 | 检测 `chatResult.source()` 为 "template" 时调用 `setCacheable(false)` |
| 80 | 异常处理 - DB 查询无 try-catch | `queryStats()` 中 DB 异常中断 Agent | 增加 try-catch, 异常时返回空统计 |
| 81 | 空指针防护 - JSON 序列化 | `objectMapper.writeValueAsString(stats)` 可能抛异常 | 新增 `safeJson()` 方法, 失败回退 toString |

### 18. ImageDesignAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 82 | **缓存污染 - 时效性结果缓存** | DALL-E 生成的图片 URL 具有时效性, 缓存后可能返回失效链接 | 覆盖 `supportsSemanticCache()` 返回 false |
| 83 | 异常处理 - DALL-E 调用无 try-catch | 图片生成 API 异常中断 Agent | 增加 try-catch, 失败降级返回默认图 URL |
| 84 | 空指针防护 - prompt 为 null | `Map.of("prompt", prompt)` 中 prompt 为 null 时 NPE | 增加 `prompt == null ? "" : prompt` |
| 85 | 空指针防护 - 响应解析 | DALL-E 响应中 url 节点可能缺失 | 增加 `isMissingNode()` 和 `isBlank()` 检查 |

### 19. OptimizeAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 86 | 空指针防护 - analysisData 类型不安全 | `params.get("analysisData")` 可能返回非 Map 类型, 直接强转 ClassCastException | 新增 `resolveAnalysisData()` 方法, 使用 `instanceof Map<?, ?>` 类型检查 |
| 87 | 缓存污染 - 模板兜底写入缓存 | 同 DataAnalysisAgent | 检测 template source 时调用 `setCacheable(false)` |
| 88 | 异常处理 - A2A 消息处理无 try-catch | `onAnalysisMessage()` 异常中断消息订阅 | 增加 try-catch |
| 89 | 空指针防护 - JSON 序列化 | 同 DataAnalysisAgent | 新增 `safeJson()` 方法 |

### 20. TopicPlanningAgent.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 90 | 空指针防护 | `Document.getContent()` 可能为 null | 增加 `filter(Objects::nonNull)` |
| 91 | 代码重复 | 截断逻辑与 BaseAgent 重复 | 复用 BaseAgent 的 `truncate()` 方法 |

---

## 七、api/ API 层模块

### 21. AIGateway.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 92 | **缓存污染 - 模板兜底写入缓存** | 模型降级到模板兜底文案时, 兜底文案被写入语义缓存 | 增加判断 `!"template".equals(chatResult.source())` 才写入缓存 |
| 93 | 空指针防护 - prompt 为 null | `estimateTokens()` 中 prompt 为 null 时 NPE | 增加 `prompt == null ? 0 : prompt.length()` |
| 94 | 日志规范 | 缺少调用完成日志的上下文信息 | 增加 model/source 信息到日志 |

### 22. KnowledgeController.java

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 95 | Spring AI 1.0.0 GA API 兼容性 | `embeddingModel.embed()` 返回 `List<Double>`, 与 GA 版 `float[]` 不兼容 | 改为 `float[]` 返回类型, 传给 `qdrantVectorStoreService.upsert()` |
| 96 | 空指针防护 - 向量化结果 | embed 返回 null/空数组时继续写入 Qdrant 导致异常 | 增加 null/空数组检查 |
| 97 | 异常处理 - 向量化无 try-catch | 向量化失败中断上传流程 (文档已落库但无法返回) | 增加 try-catch, 向量化失败仅记录 warn, 返回 `vectorized=false` |
| 98 | 空指针防护 - topK 非法值 | `topK <= 0` 会导致 BM25 `LIMIT ?` 和 rerank limit 异常 | 增加防御性归一化 `topK <= 0 ? 5 : topK` |
| 99 | 异常处理 - 租户解析无 try-catch | `tenantRepository.findByTenantCode()` 异常中断请求 | 抽取 `resolveTenantIdLong()` 方法, 增加 try-catch |
| 100 | 日志规范 | 向量化失败无日志 | 增加 `log.warn` 记录失败 docId |

### 23. 其他 Controller (Analysis/Content/Evaluation/Image/Optimize/Publish/Quota/Topic)

| # | 问题类型 | 问题描述 | 修复方案 |
|---|---------|---------|---------|
| 101 | 异常处理 - 租户解析无 try-catch | EvaluationController/QuotaController 中租户解析无容错 | 抽取 `resolveTenantIdLong()` 方法, 增加 try-catch 并记录 warn 日志 |
| 102 | 空指针防护 - 请求参数防御 | ContentController 中 `request.getKeywords()` 可能 null | 增加 `request.getKeywords() == null ? List.of() : request.getKeywords()` |
| 103 | 空指针防护 - 请求参数防御 | OptimizeController 中 `request.getAnalysisData()` 可能 null | 增加 `request.getAnalysisData() == null ? Map.of() : request.getAnalysisData()` |
| 104 | 参数校验 | 所有 POST 接口请求体缺少 `@Valid` 注解 | 所有 `@RequestBody` 参数增加 `@Valid` 注解, 配合 DTO 中的 `@NotBlank` 等校验 |
| 105 | 日志规范 | EvaluationController/QuotaController 缺少 `@Slf4j` | 增加 `@Slf4j` 注解 |

---

## 修复统计汇总

| 问题类型 | 修复数量 | 严重程度 |
|---------|---------|---------|
| Spring AI 1.0.0 GA API 兼容性 | 5 | 致命 (编译失败) |
| SQL 注入 | 1 | 致命 (安全漏洞) |
| String.format 注入 | 5 | 严重 (运行时异常) |
| 线程安全 (ThreadLocal 泄漏/竞态) | 6 | 严重 |
| 缓存污染 (模板兜底/副作用缓存) | 6 | 严重 |
| 异常处理 (静默吞异常/无 try-catch) | 25 | 高 |
| 空指针防护 | 30 | 高 |
| 资源管理 (超时/线程池/连接) | 10 | 高 |
| 日志规范 | 8 | 中 |
| 代码重复 | 3 | 中 |
| 参数校验 | 2 | 中 |
| 空指针防护 - 类型不安全 | 3 | 高 |
| **合计** | **105** | |

---

## 涉及文件清单 (共 23 个文件)

### capability/retrieval/ (5 个文件)
- `QdrantVectorStoreService.java`
- `HybridRetriever.java`
- `BM25Service.java`
- `CrossEncoderReranker.java`
- `RRFFusionStrategy.java`

### capability/fallback/ (2 个文件)
- `ModelFallbackChain.java`
- `CircuitBreakerManager.java`

### capability/cache/ (1 个文件)
- `SemanticCacheService.java`

### capability/evaluation/ (4 个文件)
- `RagasEvaluator.java`
- `FaithfulnessEvaluator.java`
- `AnswerRelevancyEvaluator.java`
- `ContextPrecisionEvaluator.java`

### capability/validation/ (1 个文件)
- `StructuredOutputGuard.java`

### agent/ (7 个文件)
- `BaseAgent.java`
- `ContentCreationAgent.java`
- `PublishAgent.java`
- `DataAnalysisAgent.java`
- `ImageDesignAgent.java`
- `OptimizeAgent.java`
- `TopicPlanningAgent.java`

### api/ (3 个文件)
- `AIGateway.java`
- `KnowledgeController.java`
- 其他 Controller (EvaluationController, QuotaController 等共性修复)
