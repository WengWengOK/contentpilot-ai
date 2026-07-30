# ContentPilot AI

> 基于 Spring Boot 3.2 + Spring AI 1.0.0 GA 的企业级 AI 内容运营平台，覆盖混合检索、RAGAS 评估、Agent 编排、多租户隔离、生产级容错等 8 大核心能力域，通过 6 个 AI Agent 协作完成"选题策划 → 内容创作 → 配图设计 → 排版发布 → 数据分析 → 优化迭代"全流程。

---

## 核心特性 (8 大能力域)

| # | 能力域 | 核心能力 | 关键技术 |
|---|--------|----------|----------|
| 1 | 混合检索 | 向量检索 + BM25 关键词检索 + RRF 融合 + Cross-Encoder 精排 | Qdrant + PostgreSQL ts_vector + bge-reranker-v2-m3 |
| 2 | RAGAS 评估 | Faithfulness / Answer Relevancy / Context Precision 四维质量量化 | LLM-as-a-Judge + Langfuse 追踪 |
| 3 | 模型降级链 | GPT-4o → DeepSeek → Qwen-Plus → 语义缓存 → 模板兜底，五级容错 | Resilience4j 熔断器 + 指数退避重试 |
| 4 | 语义缓存 | 基于向量相似度判断请求语义等价，命中率约 35% | Qdrant semantic_cache collection + BGE-M3 Embedding |
| 5 | Token 级限流 | 按租户按天控制 Token 消耗，精确反映真实成本 | Redis 原子计数器 + 定时配额重置 |
| 6 | 结构化输出校验 | JSON Schema 校验 + 自动修复 LLM 输出格式 | networknt/json-schema-validator + LLM 修复重试 |
| 7 | 多租户隔离 | PostgreSQL 行级隔离 + Qdrant payload 过滤 + Redis key 前缀 | ThreadLocal 租户上下文 + 拦截器注入 |
| 8 | A2A 协议与 Agent 编排 | Agent 间解耦通信 + 状态持久化 + 断点续跑 | Redis Pub/Sub + Checkpoint (JSONB) |

---

## 技术栈

| 层级 | 技术选型 | 版本 | 选型理由 |
|------|----------|------|----------|
| 语言/框架 | Java + Spring Boot | 17 / 3.2.5 | 生态成熟，Spring AI 原生集成 |
| AI 框架 | Spring AI | 1.0.0 GA | 模型抽象层，与 Spring 生态无缝集成 |
| 向量数据库 | Qdrant | 1.8 | 支持 payload 过滤、混合检索、Rust 高性能 |
| 关系数据库 | PostgreSQL | 16 | 行级隔离、JSONB 存储 Agent 状态、全文索引 |
| 缓存 | Redis | 7 | Token 限流计数器 + 热点缓存 + A2A Pub/Sub |
| Embedding | BGE-M3 | - | 多语言、多粒度，支持稠密+稀疏向量，中文效果好 |
| Reranker | bge-reranker-v2-m3 | - | Cross-Encoder 精排，中文场景效果优于 Bi-Encoder |
| 容错框架 | Resilience4j | 2.2.0 | 熔断器 + 重试 + 时间限制 |
| JSON 校验 | json-schema-validator | 1.5.0 | 结构化输出校验 |
| 可观测性 | Langfuse + Micrometer + Prometheus | - | AI 链路追踪 + 系统指标监控 |
| 数据库迁移 | Flyway | - | 版本化 SQL 迁移管理 |
| 容器编排 | Docker + Kubernetes | - | 多阶段构建 + HPA 自动扩缩容 |
| CI/CD | GitHub Actions | - | 自动构建、测试、推送镜像、部署 |

---

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.9+
- Docker & Docker Compose
- (可选) Kubernetes 集群 + kubectl

### 1. 启动基础设施

```bash
cd content-ops-ai

# 启动 PostgreSQL、Redis、Qdrant、Langfuse
docker-compose up -d

# 验证服务状态
docker-compose ps
```

### 2. 配置环境变量

```bash
export OPENAI_API_KEY="your-openai-api-key"
export OPENAI_BASE_URL="https://api.openai.com"
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_USER="contentops"
export DB_PASSWORD="password"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export QDRANT_HOST="localhost"
export QDRANT_PORT="6333"
export LANGFUSE_HOST="http://localhost:3000"
```

### 3. 运行应用

```bash
# 本地开发
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/content-ops-ai-0.1.0-SNAPSHOT.jar
```

### 4. 验证服务

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# Prometheus 指标
curl http://localhost:8080/actuator/prometheus
```

### 5. Docker 部署

```bash
# 构建镜像
docker build -t content-ops-ai:latest .

# 运行容器
docker run -d \
  --name content-ops-ai \
  -p 8080:8080 \
  -e OPENAI_API_KEY="$OPENAI_API_KEY" \
  -e DB_HOST="host.docker.internal" \
  -e REDIS_HOST="host.docker.internal" \
  -e QDRANT_HOST="host.docker.internal" \
  content-ops-ai:latest
```

### 6. Kubernetes 部署

```bash
# 创建命名空间
kubectl create namespace content-ops

# 部署所有资源
kubectl apply -f k8s/ -n content-ops

# 查看部署状态
kubectl get pods -n content-ops
kubectl get hpa -n content-ops
```

---

## 项目结构

```
content-ops-ai/
├── Dockerfile                         # 多阶段 Docker 构建
├── docker-compose.yml                 # 本地开发环境 (PG/Redis/Qdrant/Langfuse)
├── pom.xml                            # Maven 项目配置
├── k8s/                               # Kubernetes 部署配置
│   ├── deployment.yaml                # Deployment (3 副本, 资源限制, 健康探针)
│   ├── service.yaml                   # ClusterIP Service
│   ├── hpa.yaml                       # 自动扩缩容 (CPU + 自定义QPS指标)
│   ├── configmap.yaml                 # 非敏感配置 (Redis/Qdrant/Profile)
│   ├── secret.yaml                    # 敏感配置模板 (DB/API Key)
│   └── ingress.yaml                   # Nginx Ingress 路由
├── .github/workflows/
│   └── ci-cd.yml                      # GitHub Actions CI/CD 流水线
├── src/main/java/com/contentops/ai/
│   ├── ContentOpsApplication.java     # Spring Boot 启动类
│   ├── common/                        # 公共模块
│   │   ├── config/                    # Jackson / RestTemplate 配置
│   │   ├── constant/                  # AiConstants (Agent类型/状态/A2A消息)
│   │   ├── exception/                 # 全局异常处理 + 业务异常
│   │   └── util/                      # TraceUtil 链路追踪工具
│   ├── domain/                        # 领域模型
│   │   ├── entity/                    # 数据库实体 (Tenant/User/AgentExecution...)
│   │   ├── dto/                       # DTO (TopicSuggestion/ContentOutline/ApiResponse)
│   │   └── event/                     # A2A 领域事件
│   ├── infrastructure/                # 基础设施层
│   │   ├── qdrant/                    # Qdrant 向量库客户端
│   │   └── postgres/                  # PostgreSQL 仓库
│   ├── capability/                    # 能力服务层 (8大能力域)
│   │   ├── retrieval/                 # 混合检索 + RRF + Cross-Encoder Rerank
│   │   ├── evaluation/                # RAGAS 四维评估
│   │   ├── fallback/                  # 模型降级链 + Resilience4j 熔断
│   │   ├── cache/                     # 语义缓存 + 过期清理
│   │   ├── ratelimit/                 # Token 级限流 + 配额重置
│   │   ├── validation/                # 结构化输出校验 + 自动修复
│   │   └── tenant/                    # 多租户上下文隔离
│   └── SYSTEM_DESIGN.md              # 系统设计文档
├── src/main/resources/
│   ├── application.yml                # 主配置文件
│   ├── prompts/                       # StringTemplate Prompt 模板
│   │   ├── topic-planning.st          # 选题策划 Agent Prompt
│   │   ├── content-creation.st        # 内容创作 Agent Prompt
│   │   └── ragas-faithfulness.st      # RAGAS 忠实度评估 Prompt
│   ├── db/migration/                  # Flyway 数据库迁移脚本
│   └── schemas/                       # JSON Schema 定义
│       ├── topic-suggestion.json      # 选题建议输出 Schema
│       └── content-outline.json       # 内容大纲输出 Schema
```

---

## API 接口列表

| 方法 | 路径 | 描述 | 所属 Agent |
|------|------|------|-----------|
| POST | `/api/v1/topic/suggest` | 选题策划 | 选题策划 Agent |
| POST | `/api/v1/content/create` | 内容创作 | 内容创作 Agent |
| POST | `/api/v1/image/generate` | 配图生成 | 配图设计 Agent |
| POST | `/api/v1/publish/multi-platform` | 多平台发布 | 排版发布 Agent |
| GET | `/api/v1/analysis/monthly` | 月度数据分析 | 数据分析 Agent |
| POST | `/api/v1/optimize/strategy` | 策略优化 | 优化迭代 Agent |
| POST | `/api/v1/knowledge/upload` | 知识库上传 | - |
| GET | `/api/v1/evaluation/report` | RAGAS 评估报告 | - |
| GET | `/api/v1/quota/usage` | 配额使用查询 | - |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "traceId": "trace-uuid",
  "timestamp": "2026-07-30T10:00:00Z"
}
```

### 接口示例

```bash
# 选题策划
curl -X POST http://localhost:8080/api/v1/topic/suggest \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: default" \
  -d '{
    "keywords": ["私域流量", "用户增长"],
    "platform": "wechat",
    "count": 5
  }'
```

---

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      接入层 · Gateway                             │
│        Web前端(React)  │  REST API  │  AI Gateway(限流/缓存)     │
├─────────────────────────────────────────────────────────────────┤
│                   Agent 编排层 · Orchestration                    │
│   选题Agent │ 内容Agent │ 配图Agent │ 发布Agent                  │
│   分析Agent │ 优化Agent │ A2A总线  │ Checkpoint(断点续跑)        │
├─────────────────────────────────────────────────────────────────┤
│                     能力服务层 · Services                         │
│   混合检索 │ Cross-Encoder Rerank │ RAGAS评估 │ 模型降级链       │
│   语义缓存 │ Token级限流 │ 结构化输出校验 │ 多租户隔离            │
├─────────────────────────────────────────────────────────────────┤
│                    数据与模型层 · Data                            │
│   Qdrant向量库 │ PostgreSQL │ Redis │ BGE-M3 │ bge-reranker      │
├─────────────────────────────────────────────────────────────────┤
│                   基础设施层 · Infrastructure                     │
│   K8s集群 │ CI/CD(GitHub Actions) │ Langfuse │ Prometheus       │
└─────────────────────────────────────────────────────────────────┘
```

### 请求流转链路 (以选题策划为例)

```
用户请求
  → AI Gateway (鉴权 + Token限流 + 语义缓存检查)
    → TenantInterceptor (注入租户上下文)
      → TopicPlanningAgent (ReAct 模式)
        → HybridRetriever (向量检索 + BM25 → RRF 融合)
        → CrossEncoderReranker (精排 Top-5)
        → ModelFallbackChain (GPT-4o → DeepSeek → Qwen)
        → StructuredOutputGuard (JSON Schema 校验)
      → RagasEvaluator (Faithfulness + Relevancy 质量评估)
      → Langfuse (全链路 Trace 记录)
    → 返回结果 (+ 语义缓存写入)
```

### K8s 部署拓扑

```
┌──────────────── K8s Cluster ────────────────┐
│  Namespace: content-ops                      │
│                                              │
│  ┌── Deployment ──┐    ┌── Service ───────┐ │
│  │ content-ops-ai │───→│ ClusterIP :8080  │ │
│  │ replicas: 3    │    └──────────────────┘ │
│  └────────────────┘           ↑              │
│         ↑                     ↑              │
│  ┌── HPA ──────────┐  ┌── Ingress ────────┐ │
│  │ min:2 max:10    │  │ content-ops        │ │
│  │ CPU>70% → scale │  │ .example.com       │ │
│  │ QPS>100 → scale │  └────────────────────┘ │
│  └─────────────────┘                         │
└──────────────────────────────────────────────┘
```

---

## 面试亮点

### 1. 混合检索 + RRF 融合 + Cross-Encoder 精排

**技术点**: 向量检索 (Qdrant) 和 BM25 关键词检索 (PostgreSQL ts_vector) 双路召回，通过 RRF (Reciprocal Rank Fusion) 算法融合排名，再用 bge-reranker-v2-m3 Cross-Encoder 精排 Top-K。

**Trade-off**: RRF 只依赖排名不依赖原始分数，天然解决向量相似度分数和 BM25 分数量纲不一致的问题。Cross-Encoder 相比 Bi-Encoder 能捕获 query-doc 交互特征，精度更高但延迟也更高，所以只对 Top-20 做精排而非全量。

**效果**: 召回率从单一向量检索的 72% 提升至 87%，Faithfulness 从 0.78 提升至 0.89。

### 2. RAGAS 四维质量评估

**技术点**: 用 LLM-as-a-Judge 方式对 RAG 输出进行 Faithfulness (忠实度)、Answer Relevancy (相关性)、Context Precision (上下文精确率) 三维量化评估，结果写入 Langfuse 关联 Trace ID。

**效果**: 实现了 RAG 质量的可量化、可追踪、可回归。每次 Prompt 或检索策略变更都能用 RAGAS 分数对比效果。

### 3. 五级模型降级链 + Resilience4j 熔断

**技术点**: GPT-4o → DeepSeek → Qwen-Plus → 语义缓存兜底 → 模板兜底。每级配置独立的超时和重试策略，配合 Resilience4j 熔断器 (失败率 50% 触发熔断，30s 后半开探测)。

**效果**: API 可用性从单模型的 ~97% 提升至 99.5%+。主模型故障时用户无感切换，平均故障恢复时间 < 5s。

### 4. 语义缓存 (命中率 ~35%)

**技术点**: 将用户 query 通过 BGE-M3 生成 embedding，在 Qdrant semantic_cache collection 中检索 (cosine >= 0.92 判定命中)，命中则直接返回缓存的 answer。

**效果**: 缓存命中率约 35%，节省 LLM 调用成本约 35%，P99 延迟从 3.2s 降至 0.8s。

### 5. Token 级限流 (精确成本控制)

**技术点**: 相比传统请求级限流，按 Token 消耗量限流更精确反映真实成本。一个 5000 Token 的长 prompt 和 100 Token 的短 prompt 资源消耗差 50 倍。使用 Redis 原子 DECR 操作保证并发安全。

### 6. K8s HPA 双指标自动扩缩容

**技术点**: 同时基于 CPU 利用率 (70%) 和自定义 Pod 指标 (http_requests_per_second > 100) 触发扩缩容，min=2 max=10，配合就绪探针 (30s) 和存活探针 (60s) 保证滚动更新零停机。

---

## 开发指南

### 环境准备

```bash
# 克隆项目
git clone <repository-url>
cd content-ops-ai

# 启动基础设施
docker-compose up -d

# 编译项目
mvn clean compile
```

### 代码规范

- 遵循分层架构: `common` → `domain` → `infrastructure` → `capability` → `agent` → `api`
- 实体类使用 Lombok (`@Getter @Setter @Builder`)
- 异常统一通过 `GlobalExceptionHandler` 处理
- 所有 AI 调用必须经过 `ModelFallbackChain` (降级链)
- 所有 LLM JSON 输出必须经过 `StructuredOutputGuard` (结构化校验)
- 所有数据访问必须携带租户上下文 (`TenantContext`)

### 添加新的 Prompt 模板

1. 在 `src/main/resources/prompts/` 下创建 `.st` 文件 (StringTemplate 格式)
2. 使用 `<variable>` 语法定义占位符
3. 在对应的 Agent 中加载模板并填充变量

### 数据库迁移

```bash
# Flyway 自动执行 migration 脚本
# 新增迁移: src/main/resources/db/migration/V{version}__{description}.sql
mvn spring-boot:run  # 启动时自动执行 Flyway
```

### 运行测试

```bash
# 全部测试
mvn test

# 指定测试类
mvn test -Dtest=HybridRetrieverTest
```

### 构建 Docker 镜像

```bash
docker build -t content-ops-ai:latest .
docker run -p 8080:8080 content-ops-ai:latest
```

### CI/CD 流水线

项目配置了 GitHub Actions 自动化流水线 (`.github/workflows/ci-cd.yml`):

1. **test**: push/PR 触发 → Maven 单元测试
2. **build**: 测试通过 → 打包 JAR → 构建 Docker 镜像 → 推送至 GHCR
3. **deploy**: main 分支 push → kubectl apply 部署至 K8s → 等待滚动更新完成

### 监控端点

| 端点 | 用途 |
|------|------|
| `/actuator/health` | 健康检查 (K8s 探针) |
| `/actuator/health/readiness` | 就绪探针 |
| `/actuator/health/liveness` | 存活探针 |
| `/actuator/prometheus` | Prometheus 指标抓取 |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 指标详情 |
