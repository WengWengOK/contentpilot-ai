# ContentOps AI — 系统设计文档

> 基于 content-ops-platform 升级的企业级 AI 内容运营平台，覆盖混合检索、RAGAS 评估、Agent 编排、多租户隔离、生产级容错等 8 大核心能力域。

---

## 1 项目概述

### 1.1 项目定位

ContentOps AI 是一个面向 MCN 机构和品牌方的智能内容运营平台，通过 6 个 AI Agent 协作完成"选题策划 → 内容创作 → 配图设计 → 排版发布 → 数据分析 → 优化迭代"全流程。项目以 Spring Boot 3.x 为基座，集成 Spring AI 和 LangChain4j，融合混合检索、RAGAS 评估、A2A 协议、模型降级链等大厂级技术能力。

### 1.2 核心目标

| 目标 | 衡量标准 |
|------|----------|
| RAG 质量可量化 | 召回率 ≥ 85%，Faithfulness ≥ 0.85 |
| 生产级可靠性 | API 可用性 ≥ 99.5%，降级链覆盖全部 LLM 调用 |
| 企业级隔离 | 多租户数据隔离 + Token 级配额管理 |
| 全链路可观测 | Trace ID 串联，故障定位 < 5 分钟 |
| 面试可讲述 | 每个技术选型有 Trade-off 分析 + 效果数据 |

### 1.3 技术栈总览

| 层级 | 技术选型 | 选型理由 |
|------|----------|----------|
| 语言/框架 | Java 17 + Spring Boot 3.2 | 生态成熟，Spring AI 原生集成 |
| AI 框架 | Spring AI 1.0 + LangChain4j 0.35 | Spring AI 管模型抽象，LangChain4j 管 Agent 编排 |
| 向量数据库 | Qdrant 1.8 | 支持 payload 过滤、混合检索、高性能 |
| 关系数据库 | PostgreSQL 16 | 行级隔离、JSONB 存储 Agent 状态 |
| 缓存 | Redis 7 | Token 限流计数器 + 热点缓存 |
| Embedding | BGE-M3 (HuggingFace) | 多语言、多粒度，支持稠密+稀疏向量 |
| Reranker | bge-reranker-v2-m3 | Cross-Encoder，中文场景效果好 |
| 可观测性 | Langfuse + Micrometer + Prometheus | AI 链路追踪 + 系统指标监控 |
| 容器编排 | K8s + Helm | HPA 自动扩缩容 + 滚动更新 |
| CI/CD | GitHub Actions | 自动构建、测试、推送镜像 |

---

## 2 系统架构

### 2.1 五层架构

```
┌─────────────────────────────────────────────────────────┐
│                    接入层 · Gateway                       │
│  Web前端(React)  │  REST API  │  AI Gateway(限流/缓存)   │
├─────────────────────────────────────────────────────────┤
│                 Agent 编排层 · Orchestration              │
│  选题Agent │ 内容Agent │ 配图Agent │ 发布Agent           │
│  分析Agent │ 优化Agent │ A2A总线  │ Checkpoint           │
├─────────────────────────────────────────────────────────┤
│                  能力服务层 · Services                    │
│  混合检索 │ Rerank │ RAGAS评估 │ 降级链                  │
│  语义缓存 │ Token限流 │ 结构化输出校验                    │
├─────────────────────────────────────────────────────────┤
│                数据与模型层 · Data                        │
│  Qdrant向量库 │ PostgreSQL │ Redis │ BGE-M3 │ Reranker  │
├─────────────────────────────────────────────────────────┤
│                基础设施层 · Infrastructure                │
│  K8s集群 │ CI/CD │ Langfuse │ Prometheus │ 多租户隔离    │
└─────────────────────────────────────────────────────────┘
```

### 2.2 请求流转链路

以"用户发起选题策划"为例：

```
用户请求 → AI Gateway(鉴权+限流+语义缓存检查)
  → TenantInterceptor(注入租户上下文)
  → TopicPlanningAgent(ReAct模式)
    → HybridRetriever(向量检索+BM25 → RRF融合)
    → CrossEncoderReranker(精排Top-5)
    → ModelFallbackChain(GPT-4o → DeepSeek → Qwen)
    → StructuredOutputGuard(JSON Schema校验)
  → RagasEvaluator(质量评估)
  → Langfuse(全链路Trace记录)
  → 返回结果(+语义缓存写入)
```

### 2.3 模块依赖关系

```
common (公共工具、常量、异常)
  ↑
domain (实体、DTO、事件)
  ↑
infrastructure (Qdrant/PG/Redis/模型客户端)
  ↑
capability (混合检索/Rerank/RAGAS/降级链/缓存/限流/校验)
  ↑
agent (6个Agent + A2A + Checkpoint)
  ↑
api (REST控制器 + Gateway + 拦截器)
  ↑
application (启动类 + 配置)
```

---

## 3 项目结构

```
content-ops-ai/
├── pom.xml                          # 父POM
├── docker-compose.yml               # 本地开发环境
├── k8s/                             # K8s部署配置
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── hpa.yaml
│   └── configmap.yaml
├── src/main/java/com/contentops/ai/
│   ├── ContentOpsApplication.java   # 启动类
│   ├── common/                      # 公共模块
│   │   ├── config/                  # 配置类
│   │   ├── exception/               # 全局异常
│   │   ├── constant/                # 常量
│   │   └── util/                    # 工具类
│   ├── domain/                      # 领域模型
│   │   ├── entity/                  # 数据库实体
│   │   ├── dto/                     # 数据传输对象
│   │   └── event/                   # 领域事件
│   ├── infrastructure/              # 基础设施层
│   │   ├── qdrant/                  # Qdrant客户端
│   │   ├── postgres/                # PostgreSQL仓库
│   │   ├── redis/                   # Redis客户端
│   │   └── llm/                     # LLM模型客户端
│   ├── capability/                  # 能力服务层
│   │   ├── retrieval/               # 混合检索+RRF+Rerank
│   │   ├── evaluation/              # RAGAS评估
│   │   ├── fallback/                # 模型降级链+熔断
│   │   ├── cache/                   # 语义缓存
│   │   ├── ratelimit/               # Token级限流
│   │   ├── validation/              # 结构化输出校验
│   │   └── tenant/                  # 多租户隔离
│   ├── agent/                       # Agent编排层
│   │   ├── topic/                   # 选题策划Agent
│   │   ├── content/                 # 内容创作Agent
│   │   ├── image/                   # 配图设计Agent
│   │   ├── publish/                 # 排版发布Agent
│   │   ├── analysis/                # 数据分析Agent
│   │   ├── optimize/                # 优化迭代Agent
│   │   ├── a2a/                     # A2A协议总线
│   │   └── checkpoint/              # Agent状态持久化
│   └── api/                         # API层
│       ├── controller/              # REST控制器
│       ├── gateway/                 # AI Gateway
│       ├── interceptor/             # 拦截器
│       └── dto/                     # API层DTO
├── src/main/resources/
│   ├── application.yml              # 主配置
│   ├── application-dev.yml          # 开发环境
│   ├── application-prod.yml         # 生产环境
│   ├── prompts/                     # Prompt模板
│   │   ├── topic-planning.st
│   │   ├── content-creation.st
│   │   └── ...
│   ├── db/migration/                # Flyway迁移脚本
│   └── schemas/                     # JSON Schema定义
│       ├── topic-suggestion.json
│       └── content-outline.json
└── src/test/java/                   # 测试
```

---

## 4 核心模块详细设计

### 4.1 混合检索模块 (Hybrid Retrieval)

**所在包**: `capability.retrieval`

**职责**: 将向量检索和 BM25 关键词检索的结果通过 RRF 算法融合，再由 Cross-Encoder 精排，输出 Top-K 结果。

**核心类**:

| 类名 | 职责 |
|------|------|
| `HybridRetriever` | 编排向量检索 + BM25 + RRF + Rerank 全流程 |
| `QdrantVectorStore` | 封装 Qdrant 向量检索，支持 payload 过滤 |
| `BM25Service` | 基于 PostgreSQL 全文索引实现 BM25 关键词检索 |
| `RRFFusionStrategy` | RRF 融合算法实现 |
| `CrossEncoderReranker` | 调用 bge-reranker-v2-m3 进行精排 |

**关键流程**:

```
输入: query, tenantId, topK(默认5)
  │
  ├── 1. 向量检索: BGE-M3 Embedding → Qdrant similaritySearch(topK=20, filter=tenant_id)
  ├── 2. BM25检索: PostgreSQL ts_vector查询(topK=20, filter=tenant_id)
  ├── 3. RRF融合: score(d) = Σ 1/(k + rank_i), k=60
  ├── 4. Cross-Encoder Rerank: query+doc → bge-reranker → 取topK
  └── 输出: List<Document> (topK条, 按rerank分数降序)
```

**RRF 融合公式**:

```
score(d) = Σ_{i=1}^{n} 1 / (k + rank_i(d))

其中:
  n = 检索路数(当前为2: 向量+BM25)
  k = 平滑参数(默认60, 控制排名靠后结果的权重衰减)
  rank_i(d) = 文档d在第i路检索中的排名(从1开始)
```

**选型 Trade-off**:

- 向量检索用 BGE-M3 而非 OpenAI text-embedding-3：BGE-M3 支持稠密+稀疏向量，中文效果更好，且可本地部署降低成本
- BM25 用 PostgreSQL 全文索引而非 Elasticsearch：减少一个中间件依赖，PG 的 ts_vector 对中小规模数据足够
- RRF 而非加权平均：RRF 只依赖排名不依赖原始分数，天然解决量纲不一致问题
- Cross-Encoder 而非 Bi-Encoder 做 Rerank：Cross-Encoder 能捕获 query-doc 交互特征，精度更高

### 4.2 RAGAS 评估模块

**所在包**: `capability.evaluation`

**职责**: 对 RAG 系统的输出进行四维质量评估，生成可追踪的量化指标。

**核心类**:

| 类名 | 职责 |
|------|------|
| `RagasEvaluator` | 编排四维评估流程 |
| `FaithfulnessEvaluator` | 忠实度评估：answer 中的 claim 是否被 contexts 支持 |
| `AnswerRelevancyEvaluator` | 相关性评估：answer 是否回答了 query |
| `ContextPrecisionEvaluator` | 上下文精确率：contexts 中有用文档的比例 |
| `EvaluationRecorder` | 将评估结果记录到 Langfuse |

**评估流程**:

```
输入: query, answer, contexts
  │
  ├── 1. Faithfulness:
  │     ├── 从 answer 中提取 claims (LLM辅助)
  │     ├── 对每个 claim 判断是否被 contexts 支持 (LLM辅助)
  │     └── score = supported_claims / total_claims
  │
  ├── 2. Answer Relevancy:
  │     ├── 从 answer 反向生成可能的问题 (LLM辅助)
  │     ├── 计算生成问题与原始 query 的相似度
  │     └── score = avg_similarity
  │
  ├── 3. Context Precision:
  │     ├── 对每个 context 判断是否对回答 query 有用 (LLM辅助)
  │     └── score = useful_contexts / total_contexts
  │
  └── 4. 记录到 Langfuse (trace_id关联)
```

### 4.3 模型降级链模块

**所在包**: `capability.fallback`

**职责**: 当主模型不可用时自动切换到备用模型，配合熔断器和重试机制保障可用性。

**核心类**:

| 类名 | 职责 |
|------|------|
| `ModelFallbackChain` | 编排降级链：主模型 → 备用 → 兜底 |
| `ModelEndpoint` | 封装单个模型端点（名称、超时、重试次数） |
| `CircuitBreakerManager` | Resilience4j 熔断器管理 |
| `RetryPolicyFactory` | 指数退避重试策略 |

**降级链配置**:

```
Level 1: GPT-4o        (质量优先, 超时30s, 重试3次)
  ↓ 失败
Level 2: DeepSeek-V3   (成本优先, 超时20s, 重试2次)
  ↓ 失败
Level 3: Qwen-Plus     (可用性优先, 超时15s, 重试1次)
  ↓ 失败
Level 4: 语义缓存兜底   (返回相似历史结果)
  ↓ 未命中
Level 5: 模板兜底       (返回预设模板)
```

**熔断器参数**:

```
failureRateThreshold: 50%       (失败率阈值)
slowCallRateThreshold: 60%      (慢调用比例阈值)
waitDurationInOpenState: 30s    (熔断开启后等待时间)
slidingWindowSize: 10           (滑动窗口大小)
minimumNumberOfCalls: 5         (最少调用次数才计算)
```

### 4.4 语义缓存模块

**所在包**: `capability.cache`

**职责**: 通过向量相似度判断请求是否与历史请求语义相同，命中则直接返回缓存结果。

**核心类**:

| 类名 | 职责 |
|------|------|
| `SemanticCacheService` | 缓存读取/写入核心逻辑 |
| `CacheEntry` | 缓存条目实体 |
| `CacheEvictionJob` | 定时清理过期缓存 |

**关键参数**:

```
similarityThreshold: 0.92    (相似度阈值, 高于此值判定为命中)
cacheTTL: 24h                (缓存有效期)
maxCacheEntries: 10000       (单租户最大缓存数)
collection: "semantic_cache" (Qdrant专用collection)
```

**命中判定流程**:

```
1. 将 query 通过 BGE-M3 生成 embedding
2. 在 Qdrant semantic_cache collection 中检索 (filter: tenant_id, score >= 0.92)
3. 如果命中 → 检查 expires_at → 返回缓存的 answer
4. 如果未命中 → 正常调用 LLM → 将结果写入缓存
```

### 4.5 Token 级限流模块

**所在包**: `capability.ratelimit`

**职责**: 按租户按天控制 Token 消耗量，防止单个租户耗尽共享配额。

**核心类**:

| 类名 | 职责 |
|------|------|
| `TokenLimiter` | Token 消耗检查与扣减 |
| `QuotaService` | 配额管理（查询、设置、重置） |
| `QuotaExceededException` | 配额超限异常 |

**限流逻辑**:

```
key = "quota:{tenantId}:{yyyyMMdd}"
remaining = Redis.decrBy(key, estimatedTokens)
if remaining < 0:
    throw QuotaExceededException
    (同时回滚: Redis.incrBy(key, estimatedTokens))

每日 00:00 定时任务重置配额: Redis.SET(key, dailyQuota)
```

**为什么用 Token 级而非请求级限流**:
一个长 prompt（5000 tokens）和一个短 prompt（100 tokens）消耗的资源差 50 倍，请求级限流无法反映真实成本。

### 4.6 结构化输出校验模块

**所在包**: `capability.validation`

**职责**: 校验 LLM 输出的 JSON 格式和字段完整性，校验失败时自动修复。

**核心类**:

| 类名 | 职责 |
|------|------|
| `StructuredOutputGuard<T>` | 泛型校验器，解析+校验+修复 |
| `JsonSchemaLoader` | 从 resources/schemas/ 加载 JSON Schema |
| `OutputRepairService` | 调用 LLM 修复格式错误 |

**校验流程**:

```
1. 尝试 JSON.parse(llmOutput)
   ├── 成功 → 进入步骤2
   └── 失败 → 提取JSON片段(extractJson) → 重试步骤1

2. JsonSchema.validate(parsed)
   ├── 通过 → 返回结果
   └── 失败 → 进入步骤3

3. 自动修复: 把错误信息 + 原始输出 → LLM → 重新生成JSON
   ├── 修复成功 → 返回结果
   └── 修复失败(重试1次) → 抛出StructuredOutputException
```

### 4.7 多租户隔离模块

**所在包**: `capability.tenant`

**职责**: 确保不同租户的数据严格隔离，包括数据库行级隔离和向量库 payload 过滤。

**核心类**:

| 类名 | 职责 |
|------|------|
| `TenantContext` | ThreadLocal 存储当前请求的租户信息 |
| `TenantInterceptor` | 从 JWT Token 提取 tenantId 注入 TenantContext |
| `TenantAwareDataSource` | 数据源层面的租户隔离（可选） |

**隔离策略**:

| 数据存储 | 隔离方式 | 实现 |
|----------|----------|------|
| PostgreSQL | 行级隔离 | 每张表加 tenant_id 字段，查询自动附加 WHERE tenant_id = ? |
| Qdrant | payload 过滤 | 每个 document 的 metadata 中加 tenant_id，检索时 withFilterExpression |
| Redis | key 前缀 | key = "quota:{tenantId}:{date}" |
| Langfuse | metadata 标记 | 每条 trace 的 metadata 中加 tenant_id |

### 4.8 A2A 协议与 Agent 编排模块

**所在包**: `agent.a2a`, `agent.checkpoint`

**职责**: 实现 Agent 间的解耦通信和状态持久化。

**A2A 消息格式**:

```json
{
  "messageId": "uuid-v4",
  "fromAgent": "analysis-agent",
  "toAgent": "optimize-agent",
  "messageType": "task_delegation",
  "payload": {
    "task": "adjust_topic_strategy",
    "data": { "monthlyReport": {...} }
  },
  "correlationId": "execution-trace-id",
  "timestamp": "2026-07-30T10:00:00Z"
}
```

**消息类型**:

| 类型 | 用途 |
|------|------|
| `task_delegation` | 委托任务给另一个 Agent |
| `result` | 返回任务执行结果 |
| `query` | 查询另一个 Agent 的状态 |
| `broadcast` | 广播消息给所有订阅者 |

**Checkpoint 机制**:

```sql
CREATE TABLE agent_checkpoint (
    id           BIGSERIAL PRIMARY KEY,
    agent_id     VARCHAR(64) NOT NULL,
    execution_id VARCHAR(64) NOT NULL,
    state        JSONB NOT NULL,
    created_at   TIMESTAMP DEFAULT NOW(),
    INDEX idx_execution (execution_id, created_at DESC)
);
```

### 4.9 六大 Agent 设计

每个 Agent 遵循统一的 ReAct 模式：

```
Agent 执行循环:
  1. Thought: 分析当前状态和任务
  2. Action: 选择并调用工具
  3. Observation: 观察工具返回结果
  4. 重复 1-3 直到任务完成
  5. Final Answer: 输出最终结果

每个步骤都通过 Checkpoint 持久化，支持断点续跑。
```

| Agent | 主工具 | 升级能力 | 输出格式 |
|-------|--------|----------|----------|
| 选题策划 | HybridRetriever, 热搜API | 混合检索+Rerank+语义缓存 | TopicSuggestion JSON |
| 内容创作 | LLM, RAGContext | RAGAS评估+结构化校验+降级链 | ContentOutline JSON |
| 配图设计 | DALL-E API | 降级链+熔断+缓存兜底 | ImageUrl String |
| 排版发布 | 多平台API | 多租户+Token限流 | PublishResult JSON |
| 数据分析 | SQL, LLM | RAGAS量化+A2A发布 | AnalysisReport JSON |
| 优化迭代 | A2A接收+LLM | A2A协议+Checkpoint+降级链 | StrategyUpdate JSON |

---

## 5 数据模型设计

### 5.1 PostgreSQL 表结构

```sql
-- 租户表
CREATE TABLE tenant (
    id          BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(64) UNIQUE NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    daily_quota INTEGER DEFAULT 100000,  -- 每日Token配额
    status      VARCHAR(16) DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT NOW()
);

-- 用户表
CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenant(id),
    username    VARCHAR(64) NOT NULL,
    password    VARCHAR(256) NOT NULL,
    role        VARCHAR(32) DEFAULT 'user',  -- admin/editor/viewer
    created_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(tenant_id, username)
);

-- 知识文档表
CREATE TABLE knowledge_doc (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    title       VARCHAR(256) NOT NULL,
    content     TEXT NOT NULL,
    doc_type    VARCHAR(32) DEFAULT 'article',
    -- PostgreSQL全文索引 (BM25基础)
    tsv         tsvector GENERATED ALWAYS AS (to_tsvector('simple', title || ' ' || content)) STORED,
    -- Qdrant中对应的向量ID
    vector_id   VARCHAR(64),
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_doc_tsv ON knowledge_doc USING GIN(tsv);
CREATE INDEX idx_doc_tenant ON knowledge_doc(tenant_id);

-- Agent执行记录表
CREATE TABLE agent_execution (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    VARCHAR(64) UNIQUE NOT NULL,
    tenant_id       BIGINT NOT NULL,
    agent_type      VARCHAR(32) NOT NULL,
    status          VARCHAR(16) DEFAULT 'running',  -- running/completed/failed
    input           JSONB,
    output          JSONB,
    tokens_used     INTEGER DEFAULT 0,
    model_used      VARCHAR(64),
    trace_id        VARCHAR(64),
    started_at      TIMESTAMP DEFAULT NOW(),
    completed_at    TIMESTAMP
);

-- Agent检查点表
CREATE TABLE agent_checkpoint (
    id           BIGSERIAL PRIMARY KEY,
    agent_id     VARCHAR(64) NOT NULL,
    execution_id VARCHAR(64) NOT NULL,
    state        JSONB NOT NULL,
    created_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_checkpoint_exec ON agent_checkpoint(execution_id, created_at DESC);

-- RAGAS评估记录表
CREATE TABLE ragas_evaluation (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    execution_id      VARCHAR(64),
    query             TEXT NOT NULL,
    answer            TEXT,
    contexts          JSONB,
    faithfulness      DECIMAL(4,3),
    answer_relevancy  DECIMAL(4,3),
    context_precision DECIMAL(4,3),
    created_at        TIMESTAMP DEFAULT NOW()
);

-- 审计日志表
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    user_id     BIGINT,
    action      VARCHAR(64) NOT NULL,
    resource    VARCHAR(128),
    detail      JSONB,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### 5.2 Qdrant Collection 设计

```
Collection: knowledge_vectors
  vectors: { size: 1024, distance: Cosine }  -- BGE-M3输出维度
  payload_schema:
    tenant_id: keyword (必填, 用于过滤)
    doc_id: integer
    title: keyword
    doc_type: keyword
    created_at: datetime

Collection: semantic_cache
  vectors: { size: 1024, distance: Cosine }
  payload_schema:
    tenant_id: keyword
    query: text
    answer: text
    expires_at: datetime
```

---

## 6 API 设计

### 6.1 REST API 总览

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/v1/topic/suggest | 选题策划 |
| POST | /api/v1/content/create | 内容创作 |
| POST | /api/v1/image/generate | 配图生成 |
| POST | /api/v1/publish/multi-platform | 多平台发布 |
| GET | /api/v1/analysis/monthly | 月度数据分析 |
| POST | /api/v1/optimize/strategy | 策略优化 |
| POST | /api/v1/knowledge/upload | 知识库上传 |
| GET | /api/v1/evaluation/report | 评估报告 |
| GET | /api/v1/quota/usage | 配额使用查询 |

### 6.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "traceId": "trace-uuid",
  "timestamp": "2026-07-30T10:00:00Z"
}
```

### 6.3 核心接口示例

```
POST /api/v1/topic/suggest
Authorization: Bearer {jwt_token}
Content-Type: application/json

Request:
{
  "keywords": ["私域流量", "用户增长"],
  "platform": "wechat",
  "count": 5
}

Response:
{
  "code": 200,
  "data": {
    "suggestions": [
      {
        "title": "私域流量池的3种搭建路径",
        "angle": "对比分析",
        "hotScore": 87,
        "references": ["doc_001", "doc_042"]
      }
    ],
    "modelUsed": "gpt-4o",
    "cacheHit": false,
    "tokensUsed": 1520,
    "evaluation": {
      "faithfulness": 0.89,
      "answerRelevancy": 0.85
    }
  },
  "traceId": "trace-a1b2c3"
}
```

---

## 7 部署架构

### 7.1 K8s 部署拓扑

```
┌─────────────── K8s Cluster ───────────────┐
│                                            │
│  ┌─── Namespace: content-ops ───────────┐  │
│  │                                      │  │
│  │  ┌── Deployment ──┐  ┌── Service ─┐ │  │
│  │  │ content-ops-ai │──│ ClusterIP  │ │  │
│  │  │ replicas: 3    │  │ port: 8080 │ │  │
│  │  └────────────────┘  └────────────┘ │  │
│  │           ↑                          │  │
│  │  ┌── HPA ──────────────┐            │  │
│  │  │ min: 2, max: 10     │            │  │
│  │  │ CPU > 70% → scale   │            │  │
│  │  │ QPS > 100 → scale   │            │  │
│  │  └─────────────────────┘            │  │
│  │                                      │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  ┌── Ingress ──────────────────────────┐   │
│  │ content-ops.example.com → Service   │   │
│  └──────────────────────────────────────┘   │
│                                            │
└────────────────────────────────────────────┘

外部依赖 (StatefulSet / 独立服务):
  ├── Qdrant (StatefulSet, 3副本)
  ├── PostgreSQL (StatefulSet, 1主1从)
  ├── Redis (StatefulSet, 1主1从)
  └── Langfuse (Deployment, 1副本)
```

### 7.2 CI/CD 流水线

```
GitHub Push
  → GitHub Actions: lint + test
  → Build Docker Image
  → Push to Registry
  → Deploy to K8s (kubectl apply / helm upgrade)
  → Smoke Test
  → Notify (Slack/飞书)
```

---

## 8 实施计划

| 阶段 | 周期 | 内容 | 交付物 |
|------|------|------|--------|
| Phase 0 | Week 1 | 项目骨架 + Docker环境 | 可启动的空项目 + docker-compose |
| Phase 1 | Week 2-3 | 混合检索 + Rerank | HybridRetriever + 测试数据 |
| Phase 2 | Week 4-5 | RAGAS评估 + Langfuse | 评估报告 + 效果数据 |
| Phase 3 | Week 6-7 | 降级链 + 语义缓存 + 限流 | ModelFallbackChain + 压测数据 |
| Phase 4 | Week 8-9 | 多租户 + A2A + Checkpoint | 6个Agent联调通过 |
| Phase 5 | Week 10-11 | 结构化校验 + K8s部署 | K8s部署成功 + CI/CD流水线 |
| Phase 6 | Week 12 | 简历 + README + 面试准备 | 项目文档 + 演示视频 |

---

## 9 关键技术决策记录

### 9.1 为什么选 Qdrant 而非 Milvus

- Qdrant 的 payload 过滤是多租户隔离的天然方案，Milvus 的 partition 切换成本更高
- Qdrant 的 Rust 实现在单机场景下内存占用更低
- Qdrant 的 API 更简洁，Spring AI 有原生集成

### 9.2 为什么选 Spring AI + LangChain4j 双框架

- Spring AI 提供模型抽象层（ChatClient、EmbeddingClient），与 Spring 生态无缝集成
- LangChain4j 提供 Agent 编排能力（ReAct、工具调用、记忆管理），Spring AI 在 Agent 层面还不成熟
- 两者互补：Spring AI 管模型调用，LangChain4j 管 Agent 逻辑

### 9.3 为什么 A2A 用 Redis Pub/Sub 而非 Kafka

- Agent 间通信是实时性的，不需要持久化消息队列
- Redis 已在技术栈中，不引入额外中间件
- Pub/Sub 模式天然适合 Agent 的 publish/subscribe 通信
- 如果未来需要消息持久化，可以平滑迁移到 Redis Streams
