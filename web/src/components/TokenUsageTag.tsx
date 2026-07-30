/**
 * Token 用量标签.
 *
 * 以 ThunderboltOutlined 图标 + 数字展示本次执行消耗的 Token 数.
 */
import { Tag, Tooltip } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { formatNumber } from '@/utils';

interface TokenUsageTagProps {
  /** Token 消耗数量 */
  tokens: number;
}

export function TokenUsageTag({ tokens }: TokenUsageTagProps) {
  return (
    <Tooltip title="本次执行消耗的 Token 数 (估算)">
      <Tag icon={<ThunderboltOutlined />} color={tokens > 0 ? 'orange' : 'default'}>
        {formatNumber(tokens)} Tokens
      </Tag>
    </Tooltip>
  );
}

export default TokenUsageTag;
