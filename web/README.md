# ContentPilot AI Web

> ContentPilot AI 前端项目 — 基于 React 18 + TypeScript + Vite + Ant Design 5 的企业级 AI 内容运营平台前端，对接 Spring Boot 后端 10 个 API 接口，覆盖选题策划、内容创作、配图设计、多平台发布、数据分析、优化迭代、知识库管理、RAGAS 评估、配额管理等全流程。

---

## 技术栈

| 层级 | 技术选型 | 版本 | 选型理由 |
|------|----------|------|----------|
| 框架 | React | 18.3 | 生态系统最成熟，Concurrent Features |
| 语言 | TypeScript | 5.6 | 类型安全，大厂标配 |
| 构建工具 | Vite | 5.4 | 极速 HMR + ESBuild 预构建 |
| UI 组件库 | Ant Design | 5.21 | 企业级组件，中文友好 |
| 数据请求 | TanStack Query | 5.59 | 服务端状态管理，自动缓存/重试/失效 |
| 客户端状态 | Zustand | 5.0 | 轻量级全局状态管理 |
| 路由 | React Router | 6.26 | 嵌套路由 + 懒加载 |
| HTTP 客户端 | Axios | 1.7 | 请求/响应拦截器 |
| 图表 | ECharts | 5.5 | 数据可视化 |
| 测试 | Vitest + Testing Library | 2.1 | 快速单元测试 |
| 代码规范 | ESLint + Prettier | - | 统一代码风格 |

---

## 项目结构

```
contentpilot-ai-web/
├── src/
│   ├── api/                          # API 服务层
│   │   ├── client.ts                 # Axios 实例 + 拦截器 (X-Tenant-Id 注入 + ApiResponse 解包)
│   │   ├── topic.ts                  # 选题策划 API
│   │   ├── content.ts                # 内容创作 API
│   │   ├── image.ts                  # 配图设计 API
│   │   ├── publish.ts                # 多平台发布 API
│   │   ├── analysis.ts               # 数据分析 API
│   │   ├── optimize.ts               # 优化迭代 API
│   │   ├── knowledge.ts              # 知识库 API (上传 + 搜索)
│   │   ├── evaluation.ts             # RAGAS 评估 API
│   │   ├── quota.ts                  # 配额查询 API
│   │   └── index.ts                  # 统一导出
│   ├── types/                        # TypeScript 类型定义
│   │   └── index.ts                  # 全部接口/类型 (对应后端 DTO)
│   ├── hooks/                        # TanStack Query Hooks
│   │   ├── useTopic.ts               # useMutation - 选题建议
│   │   ├── useContent.ts             # useMutation - 内容创作
│   │   ├── useImage.ts               # useMutation - 配图生成
│   │   ├── usePublish.ts             # useMutation - 多平台发布
│   │   ├── useAnalysis.ts            # useQuery - 月度分析
│   │   ├── useOptimize.ts            # useMutation - 策略优化
│   │   ├── useKnowledge.ts           # useMutation + useQuery - 知识库
│   │   ├── useEvaluation.ts          # useQuery - 评估报告
│   │   ├── useQuota.ts               # useQuery (30s 轮询) - 配额
│   │   └── index.ts                  # 统一导出
│   ├── store/                        # Zustand 状态管理
│   │   └── useAppStore.ts            # 租户 ID + 侧边栏折叠状态
│   ├── constants/                    # 常量定义
│   │   └── index.ts                  # 平台/风格/文档类型/菜单项
│   ├── utils/                        # 工具函数
│   │   └── index.ts                  # 日期格式化/数字格式化/评分颜色
│   ├── components/                   # 共享组件
│   │   ├── Layout/
│   │   │   └── MainLayout.tsx        # 全局布局 (Sider + Header + Content)
│   │   ├── AgentResponseDisplay.tsx  # Agent 响应展示 (模型/缓存/Token/TraceId/评估)
│   │   ├── EvaluationMetrics.tsx     # RAGAS 评估指标 (Progress 进度条)
│   │   ├── ModelBadge.tsx            # 模型标识 (GPT-4o/DeepSeek/Qwen/缓存/兜底)
│   │   ├── CacheHitBadge.tsx         # 缓存命中标识
│   │   ├── TokenUsageTag.tsx         # Token 消耗标签
│   │   └── TraceIdTag.tsx            # 链路追踪 ID (可复制)
│   ├── pages/                        # 10 个业务页面
│   │   ├── Dashboard/                # 工作台总览
│   │   ├── TopicPlanning/            # 选题策划
│   │   ├── ContentCreation/          # 内容创作
│   │   ├── ImageDesign/              # 配图设计
│   │   ├── Publishing/               # 多平台发布
│   │   ├── Analysis/                 # 数据分析 (ECharts)
│   │   ├── Optimization/             # 优化迭代
│   │   ├── KnowledgeBase/            # 知识库管理 (上传 + 混合检索)
│   │   ├── Evaluation/               # RAGAS 评估报告 (雷达图)
│   │   └── Quota/                    # 配额管理 (环形进度)
│   ├── App.tsx                       # 路由配置 (React.lazy 懒加载)
│   ├── main.tsx                      # 入口 (BrowserRouter + QueryClient + ConfigProvider)
│   └── index.css                     # 全局样式
├── vite.config.ts                    # Vite 配置 (代理 + 别名 + 代码分割)
├── tsconfig.json                     # TypeScript 配置
├── package.json                      # 依赖与脚本
├── .eslintrc.cjs                     # ESLint 配置
└── .prettierrc                       # Prettier 配置
```

---

## 后端 API 对接

### 统一响应格式

```typescript
interface ApiResponse<T> {
  code: number;       // 200 表示成功
  message: string;    // 提示信息
  data: T;            // 业务数据
  traceId?: string;   // 链路追踪 ID
}
```

Axios 响应拦截器自动解包 `ApiResponse`，业务层直接获取 `data`。

### 请求头

所有请求自动注入 `X-Tenant-Id` 请求头（从 localStorage 读取，默认 `default`）。

### 接口列表

| 方法 | 路径 | 描述 | 前端 Hook | 响应数据类型 |
|------|------|------|-----------|-------------|
| POST | `/api/v1/topic/suggest` | 选题策划 | `useTopicSuggest` | `AgentResponse` (data: `TopicSuggestion[]`) |
| POST | `/api/v1/content/create` | 内容创作 | `useContentCreate` | `AgentResponse` (data: `ContentOutline`) |
| POST | `/api/v1/image/generate` | 配图生成 | `useImageGenerate` | `AgentResponse` (data: `ImageResult`) |
| POST | `/api/v1/publish/multi-platform` | 多平台发布 | `usePublishMultiPlatform` | `AgentResponse` (data: `PublishResult[]`) |
| GET | `/api/v1/analysis/monthly` | 月度分析 | `useMonthlyAnalysis` | `AgentResponse` (data: `AnalysisData`) |
| POST | `/api/v1/optimize/strategy` | 策略优化 | `useOptimizeStrategy` | `AgentResponse` (data: `OptimizeStrategy`) |
| POST | `/api/v1/knowledge/upload` | 知识上传 | `useKnowledgeUpload` | `{ id, title, vectorId, vectorized }` |
| GET | `/api/v1/knowledge/search` | 知识搜索 | `useKnowledgeSearch` | `KnowledgeSearchResult[]` |
| GET | `/api/v1/evaluation/report` | 评估报告 | `useEvaluationReport` | `RagasEvaluation[]` |
| GET | `/api/v1/quota/usage` | 配额查询 | `useQuotaUsage` | `QuotaUsage` |

### AgentResponse 结构

```typescript
interface AgentResponse {
  data: unknown;                        // 业务数据
  modelUsed: string;                    // 实际使用的模型
  cacheHit: boolean;                    // 是否命中语义缓存
  tokensUsed: number;                   // Token 消耗
  traceId: string;                      // 链路追踪 ID
  evaluation: Record<string, number> | null;  // RAGAS 评估指标
}
```

前端通过 `AgentResponseDisplay` 组件统一展示模型、缓存命中、Token 消耗、TraceId 和 RAGAS 评估指标。

---

## 快速开始

### 前置要求

- Node.js 18+
- npm 或 pnpm

### 安装与运行

```bash
# 安装依赖
npm install

# 开发模式 (默认端口 5173)
npm run dev

# 生产构建
npm run build

# 预览构建结果
npm run preview

# 代码检查
npm run lint

# 格式化代码
npm run format

# 运行测试
npm run test

# 测试覆盖率
npm run test:coverage
```

### 环境变量

在项目根目录创建 `.env.local`:

```bash
# 后端 API 地址 (开发环境通过 Vite 代理，无需配置)
VITE_API_BASE_URL=/api/v1
```

### 开发代理

Vite 开发服务器配置了 `/api` 代理到 `http://localhost:8080`，前端请求自动转发到后端 Spring Boot 服务。

---

## 面试亮点

### 1. 类型安全的 API 层设计

所有后端 DTO 在前端有对应的 TypeScript 类型定义，Axios 拦截器自动解包统一响应格式，业务层零样板代码。

### 2. TanStack Query 服务端状态管理

- POST 请求使用 `useMutation`，自动管理 loading/error/success 状态
- GET 请求使用 `useQuery`，自动缓存 + 失效 + 后台刷新
- 配额页面 30 秒轮询实时更新 Token 使用量

### 3. 路由懒加载 + 代码分割

所有页面组件使用 `React.lazy` 懒加载，配合 Vite `manualChunks` 将 React / Ant Design / ECharts 分离为独立 chunk，首屏加载更快。

### 4. 多租户隔离

Axios 请求拦截器自动注入 `X-Tenant-Id` 请求头，租户 ID 持久化到 localStorage，支持运行时切换。

### 5. Agent 执行元信息可视化

`AgentResponseDisplay` 组件统一展示模型选择、缓存命中、Token 消耗、TraceId、RAGAS 评估，让 AI 链路透明可追踪。

### 6. ECharts 数据可视化

数据分析页使用柱状图 + 饼图展示内容数据，评估报告页使用雷达图展示 RAGAS 四维指标。
