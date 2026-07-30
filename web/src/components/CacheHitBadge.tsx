/**
 * 语义缓存命中徽标.
 *
 * 命中缓存显示绿色 "缓存命中", 否则显示默认色 "实时生成".
 */
import { Tag, Tooltip } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';

interface CacheHitBadgeProps {
  /** 是否命中语义缓存 */
  cacheHit: boolean;
}

export function CacheHitBadge({ cacheHit }: CacheHitBadgeProps) {
  return (
    <Tooltip title={cacheHit ? '命中语义缓存, 未消耗 Token' : '未命中缓存, 实时调用模型'}>
      <Tag icon={<ThunderboltOutlined />} color={cacheHit ? 'green' : 'default'}>
        {cacheHit ? '缓存命中' : '实时生成'}
      </Tag>
    </Tooltip>
  );
}

export default CacheHitBadge;
