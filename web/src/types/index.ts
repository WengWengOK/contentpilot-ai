/**
 * ContentPilot AI - 全局类型定义
 *
 * 所有 API 请求/响应类型集中在此文件定义, 其余模块统一通过 `@/types` 引入。
 * 类型契约与 Spring Boot 后端 (/api/v1) 严格对齐。
 */

/* -------------------------------------------------------------------------- */
/*                           统一响应 & 基础枚举                              */
/* -------------------------------------------------------------------------- */

/**
 * 统一 API 响应结构。
 *
 * 对齐后端 `com.contentops.ai.domain.dto.ApiResponse<T>`:
 * `{ code, message, data, traceId }`
 */
export interface ApiResponse<T> {
  /** 业务状态码, 200 表示成功 */
  code: number;
  /** 提示信息 */
  message: string;
  /** 业务数据 */
  data: T;
  /** 链路追踪 ID (可选) */
  traceId?: string;
}

/** 目标发布平台 */
export type Platform = 'wechat' | 'weibo' | 'xiaohongshu' | 'douyin' | 'bilibili';

/** AI 配图风格 */
export type ImageStyle =
  | 'realistic'
  | 'cartoon'
  | 'watercolor'
  | 'oil-painting'
  | 'flat-design'
  | 'cyberpunk';

/** 知识文档类型 */
export type DocType = 'article' | 'tutorial' | 'faq' | 'report';

/* -------------------------------------------------------------------------- */
/*                            Agent 统一响应                                  */
/* -------------------------------------------------------------------------- */

/**
 * Agent 统一响应 DTO (端点 1-6 返回)。
 *
 * 对齐后端 `com.contentops.ai.agent.AgentResponse`。
 */
export interface AgentResponse {
  /** 业务数据 (因端点而异) */
  data: unknown;
  /** 实际使用的模型名 */
  modelUsed: string;
  /** 是否命中语义缓存 */
  cacheHit: boolean;
  /** 本次执行消耗的 Token 数 */
  tokensUsed: number;
  /** 链路追踪 ID */
  traceId: string;
  /** RAGAS 评估指标 (仅检索增强场景填充, 无评估时为 null) */
  evaluation: Record<string, number> | null;
}

/* -------------------------------------------------------------------------- */
/*                          业务数据类型 (端点 1-6)                           */
/* -------------------------------------------------------------------------- */

/** 选题建议 (端点 1 的 data) */
export interface TopicSuggestion {
  /** 选题标题 */
  title: string;
  /** 选题摘要 / 角度说明 */
  summary: string;
  /** 关键词列表 */
  keywords: string[];
  /** 所属分类 */
  category: string;
  /** 热度评分 (0.0 ~ 1.0) */
  trendingScore: number;
}

/** 内容大纲章节 (ContentOutline.sections 元素) */
export interface Section {
  /** 章节标题 */
  heading: string;
  /** 要点列表 */
  bulletPoints: string[];
  /** 章节顺序 (从 1 开始) */
  order: number;
}

/** 内容大纲 (端点 2 的 data) */
export interface ContentOutline {
  /** 文章标题 */
  title: string;
  /** 引言 / 导语 */
  introduction: string;
  /** 正文章节列表 */
  sections: Section[];
  /** 结语 */
  conclusion: string;
}

/** 配图生成结果 (端点 3 的 data) */
export interface ImageResult {
  /** 图片访问地址 */
  imageUrl: string;
  /** 生成使用的提示词 */
  prompt: string;
  /** 图片风格 */
  style: string;
}

/** 单平台发布结果 (端点 4 data 数组元素) */
export interface PublishResult {
  /** 平台标识 */
  platform: string;
  /** 是否发布成功 */
  success: boolean;
  /** 发布后的内容链接 */
  url: string;
  /** 发布结果说明 */
  message: string;
}

/** 热门话题 (AnalysisData.topTopics 元素) */
export interface TopTopic {
  /** 话题名称 */
  topic: string;
  /** 浏览量 */
  views: number;
}

/** 平台统计 (AnalysisData.platformStats 元素) */
export interface PlatformStat {
  /** 平台标识 */
  platform: string;
  /** 发布数量 */
  posts: number;
  /** 浏览量 */
  views: number;
}

/** 月度分析数据 (端点 5 的 data) */
export interface AnalysisData {
  /** 总发布数 */
  totalPosts: number;
  /** 总浏览量 */
  totalViews: number;
  /** 平均互动率 */
  avgEngagement: number;
  /** 热门话题列表 */
  topTopics: TopTopic[];
  /** 各平台统计 */
  platformStats: PlatformStat[];
}

/** 优先级行动项 (OptimizeStrategy.priorityActions 元素) */
export interface PriorityAction {
  /** 行动项 */
  action: string;
  /** 预期影响 */
  impact: string;
  /** 实施成本 */
  effort: string;
}

/** 优化策略 (端点 6 的 data) */
export interface OptimizeStrategy {
  /** 优化建议列表 */
  recommendations: string[];
  /** 优先级行动项列表 */
  priorityActions: PriorityAction[];
  /** 预期提升描述 */
  expectedImprovement: string;
}

/* -------------------------------------------------------------------------- */
/*                       知识库 / 评估 / 配额 (端点 7-10)                     */
/* -------------------------------------------------------------------------- */

/** 知识文档上传结果. */
export interface KnowledgeUploadResult {
  id: number;
  title: string;
  vectorId?: string;
  vectorized?: boolean;
}

/** 知识库检索结果 (端点 8 的 data 元素) */
export interface KnowledgeSearchResult {
  /** 文档 ID */
  id: string;
  /** 文档内容 */
  content: string;
  /** 相关性评分 */
  score: number;
  /** 元数据 */
  metadata: Record<string, unknown>;
  /** 来源 */
  source: string;
}

/** RAGAS 生成质量评估记录 (端点 9) */
export interface RagasEvaluation {
  /** 主键 */
  id: number;
  /** 租户 ID */
  tenantId: number;
  /** 执行 ID */
  executionId: string;
  /** 查询文本 */
  query: string;
  /** 回答文本 */
  answer: string;
  /** 上下文 (JSON 字符串) */
  contexts: string;
  /** 忠实度评分 (0~1) */
  faithfulness: number;
  /** 答案相关性评分 (0~1) */
  answerRelevancy: number;
  /** 上下文精确度评分 (0~1) */
  contextPrecision: number;
  /** 创建时间 (ISO 字符串) */
  createdAt: string;
}

/** 配额使用情况 (端点 10) */
export interface QuotaUsage {
  /** 租户标识 */
  tenantId: string;
  /** 每日配额上限 */
  dailyQuota: number;
  /** 已使用量 */
  used: number;
  /** 剩余量 */
  remaining: number;
}

/* ------------------------------ 请求参数 DTO ------------------------------ */
/* 与后端 *Request 对齐, 供 @/api 各模块的请求方法使用. */

/** 选题建议请求 (POST /api/v1/topic/suggest). */
export interface TopicSuggestRequest {
  /** 选题关键词列表 */
  keywords: string[];
  /** 目标平台 */
  platform?: string;
  /** 选题数量 */
  count?: number;
}

/** 内容创作请求 (POST /api/v1/content/create). */
export interface ContentCreateRequest {
  /** 选题标题 */
  topic: string;
  /** 关键词列表 */
  keywords?: string[];
  /** 目标平台 */
  platform?: string;
}

/** 配图生成请求 (POST /api/v1/image/generate). */
export interface ImageGenerateRequest {
  /** 图片描述 */
  description: string;
  /** 图片风格 */
  style?: string;
}

/** 多平台发布请求 (POST /api/v1/publish/multi-platform). */
export interface PublishRequest {
  /** 待发布内容 */
  content: string;
  /** 目标平台列表 */
  platforms: string[];
}

/** 优化策略请求 (POST /api/v1/optimize/strategy). */
export interface OptimizeRequest {
  /** 分析数据 (由数据分析产出或外部传入) */
  analysisData?: Record<string, unknown>;
}

/** 知识文档上传请求 (POST /api/v1/knowledge/upload). */
export interface KnowledgeUploadRequest {
  /** 文档标题 */
  title: string;
  /** 文档正文 */
  content: string;
  /** 文档类型 */
  docType?: string;
}

/* ------------------------------ 常量辅助类型 ------------------------------ */
/* 供 @/constants 中的枚举选项与菜单项使用. */

/** 平台选项 (label / value). */
export interface PlatformOption {
  label: string;
  value: string;
}

/** 图片风格选项 (label / value). */
export interface ImageStyleOption {
  label: string;
  value: string;
}

/** 文档类型选项 (label / value). */
export interface DocTypeOption {
  label: string;
  value: string;
}

/** 侧边栏菜单项. */
export interface MenuItem {
  /** 唯一标识 */
  key: string;
  /** 菜单显示文本 */
  label: string;
  /** Ant Design 图标名 (视图层据此渲染图标) */
  icon: string;
  /** 路由路径 */
  path: string;
}
