/**
 * 全局常量定义
 *
 * 包含平台、图片风格、文档类型、侧边栏菜单等枚举选项, 以及默认租户 ID。
 */
import type {
  DocTypeOption,
  ImageStyleOption,
  MenuItem,
  PlatformOption,
} from '@/types';

/** 默认租户 ID */
export const DEFAULT_TENANT_ID = 'default';

/** localStorage 中保存租户 ID 的键名 */
export const TENANT_STORAGE_KEY = 'tenantId';

/**
 * 目标平台选项 (发布渠道)
 */
export const PLATFORM_OPTIONS: PlatformOption[] = [
  { label: '微信', value: 'wechat' },
  { label: '微博', value: 'weibo' },
  { label: '小红书', value: 'xiaohongshu' },
  { label: '抖音', value: 'douyin' },
  { label: 'B站', value: 'bilibili' },
];

/**
 * AI 配图风格选项
 */
export const IMAGE_STYLE_OPTIONS: ImageStyleOption[] = [
  { label: '写实', value: 'realistic' },
  { label: '卡通', value: 'cartoon' },
  { label: '水彩', value: 'watercolor' },
  { label: '油画', value: 'oil-painting' },
  { label: '扁平', value: 'flat-design' },
  { label: '赛博朋克', value: 'cyberpunk' },
];

/**
 * 知识文档类型选项
 */
export const DOC_TYPE_OPTIONS: DocTypeOption[] = [
  { label: '文章', value: 'article' },
  { label: '教程', value: 'tutorial' },
  { label: 'FAQ', value: 'faq' },
  { label: '报告', value: 'report' },
];

/* ------------------------------ 派生 Label 映射 ------------------------------ */
/* 由上述选项数组派生 value -> label 的映射, 供各页面渲染 Tag / 文本时使用. */

/** 平台 value -> 中文标签映射. */
export const PLATFORM_LABEL_MAP: Record<string, string> = Object.fromEntries(
  PLATFORM_OPTIONS.map((opt) => [opt.value, opt.label]),
);

/** 配图风格 value -> 中文标签映射. */
export const IMAGE_STYLE_LABEL_MAP: Record<string, string> = Object.fromEntries(
  IMAGE_STYLE_OPTIONS.map((opt) => [opt.value, opt.label]),
);

/** 文档类型 value -> 中文标签映射. */
export const DOC_TYPE_LABEL_MAP: Record<string, string> = Object.fromEntries(
  DOC_TYPE_OPTIONS.map((opt) => [opt.value, opt.label]),
);

/**
 * 侧边栏菜单项 (对应 10 个业务页面)
 * icon 为 Ant Design 图标名, 可在视图层据此动态渲染 <Icon />。
 */
export const MENU_ITEMS: MenuItem[] = [
  { key: 'dashboard', label: '工作台', icon: 'dashboard', path: '/dashboard' },
  { key: 'topic-planning', label: '选题策划', icon: 'bulb', path: '/topic-planning' },
  { key: 'content-creation', label: '内容创作', icon: 'edit', path: '/content-creation' },
  { key: 'image-design', label: '配图设计', icon: 'picture', path: '/image-design' },
  { key: 'publishing', label: '多平台发布', icon: 'cloud-upload', path: '/publishing' },
  { key: 'analysis', label: '数据分析', icon: 'bar-chart', path: '/analysis' },
  { key: 'optimization', label: '优化迭代', icon: 'rocket', path: '/optimization' },
  { key: 'knowledge-base', label: '知识库', icon: 'database', path: '/knowledge-base' },
  { key: 'evaluation', label: '评估报告', icon: 'safety-certificate', path: '/evaluation' },
  { key: 'quota', label: '配额管理', icon: 'wallet', path: '/quota' },
];
