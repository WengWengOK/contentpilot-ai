/**
 * 模型标识徽标.
 *
 * 根据模型名匹配预设主题色, 未匹配时回退蓝色.
 */
import { Tag } from 'antd';
import { RobotOutlined } from '@ant-design/icons';

interface ModelBadgeProps {
  /** 实际使用的模型名 (如 gpt-4o / deepseek / qwen / semantic_cache / template_fallback) */
  model: string;
}

/** 模型名 (小写) -> Ant Design Tag 颜色. */
const MODEL_COLOR_MAP: Record<string, string> = {
  gpt: 'gold',
  'gpt-4o': 'gold',
  'gpt-4': 'gold',
  deepseek: 'blue',
  qwen: 'purple',
  semantic_cache: 'green',
  template_fallback: 'red',
};

/** 根据模型名推断 Tag 颜色. */
function resolveColor(model: string): string {
  const lower = model.toLowerCase();
  // 精确匹配优先
  if (MODEL_COLOR_MAP[lower]) return MODEL_COLOR_MAP[lower];
  // 模糊匹配
  if (lower.includes('gpt')) return 'gold';
  if (lower.includes('deepseek')) return 'blue';
  if (lower.includes('qwen')) return 'purple';
  if (lower.includes('semantic_cache') || lower.includes('cache')) return 'green';
  if (lower.includes('template_fallback') || lower.includes('fallback')) return 'red';
  return 'blue';
}

export function ModelBadge({ model }: ModelBadgeProps) {
  if (!model) return null;
  return (
    <Tag icon={<RobotOutlined />} color={resolveColor(model)}>
      {model}
    </Tag>
  );
}

export default ModelBadge;
