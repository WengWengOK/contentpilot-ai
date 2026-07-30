/**
 * 链路追踪 ID 标签 (可复制).
 *
 * 使用 Ant Design Tag + Tooltip + Typography.Text copyable 实现可视化展示与一键复制.
 */
import { Tag, Tooltip, Typography } from 'antd';

const { Text } = Typography;

interface TraceIdTagProps {
  /** 链路追踪 ID */
  traceId: string;
}

export function TraceIdTag({ traceId }: TraceIdTagProps) {
  if (!traceId) return null;

  // 过长的 traceId 仅展示首尾片段, 完整值通过 copyable 复制
  const short =
    traceId.length > 16 ? `${traceId.slice(0, 8)}...${traceId.slice(-4)}` : traceId;

  return (
    <Tooltip title={`Trace ID: ${traceId} (点击图标复制)`}>
      <Tag color="purple" style={{ padding: '0 6px' }}>
        <Text
          copyable={{ text: traceId, tooltips: ['复制 Trace ID', '已复制'] }}
          style={{ fontSize: 12, color: 'inherit' }}
        >
          {short}
        </Text>
      </Tag>
    </Tooltip>
  );
}

export default TraceIdTag;
