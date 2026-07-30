/**
 * Agent 响应统一展示容器.
 *
 * 展示 AgentResponse 的执行元信息 (模型 / 缓存 / Token / TraceId / 评估指标),
 * 并通过 children 渲染具体业务数据; 无 children 时回退为 JSON 预览.
 */
import type { ReactNode } from 'react';
import { Card, Spin, Empty, Space, Divider, Typography } from 'antd';
import { ExperimentOutlined } from '@ant-design/icons';
import type { AgentResponse } from '@/types';
import { ModelBadge } from './ModelBadge';
import { CacheHitBadge } from './CacheHitBadge';
import { TokenUsageTag } from './TokenUsageTag';
import { TraceIdTag } from './TraceIdTag';
import { EvaluationMetrics } from './EvaluationMetrics';

const { Title, Text, Paragraph } = Typography;

interface AgentResponseDisplayProps {
  /** Agent 响应数据, null 时展示空状态 */
  response: AgentResponse | null;
  /** 是否加载中 */
  loading: boolean;
  /** 业务数据渲染内容, 缺省时回退 JSON 预览 */
  children?: ReactNode;
  /** 卡片标题 (可选, 默认 "执行结果") */
  title?: string;
  /** 空状态描述文案 (可选) */
  emptyDescription?: string;
}

export function AgentResponseDisplay({
  response,
  loading,
  children,
  title = '执行结果',
  emptyDescription = '暂无结果, 请提交请求后查看',
}: AgentResponseDisplayProps) {
  return (
    <Card className="section-card" title={title}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <Spin tip="执行中..." size="large">
            <div style={{ padding: '20px' }} />
          </Spin>
        </div>
      ) : !response ? (
        <Empty description={emptyDescription} />
      ) : (
        <>
          {/* 执行元信息行 */}
          <Space size={[8, 8]} wrap>
            <ModelBadge model={response.modelUsed} />
            <CacheHitBadge cacheHit={response.cacheHit} />
            <TokenUsageTag tokens={response.tokensUsed} />
            <TraceIdTag traceId={response.traceId} />
          </Space>

          {/* RAGAS 评估指标 */}
          {response.evaluation && Object.keys(response.evaluation).length > 0 && (
            <>
              <Divider style={{ margin: '12px 0' }} />
              <Title level={5}>
                <ExperimentOutlined /> RAGAS 评估指标
              </Title>
              <EvaluationMetrics metrics={response.evaluation} />
            </>
          )}

          {/* 业务数据: children 优先, 否则 JSON 预览 */}
          <Divider style={{ margin: '12px 0' }} />
          {children ?? (
            <Paragraph>
              <Text type="secondary" style={{ fontSize: 12 }}>
                响应数据 (JSON)
              </Text>
              <pre
                style={{
                  margin: '4px 0 0',
                  padding: 12,
                  background: '#fafafa',
                  borderRadius: 6,
                  fontSize: 12,
                  overflow: 'auto',
                  maxHeight: 400,
                }}
              >
                {JSON.stringify(response.data, null, 2)}
              </pre>
            </Paragraph>
          )}
        </>
      )}
    </Card>
  );
}

export default AgentResponseDisplay;
