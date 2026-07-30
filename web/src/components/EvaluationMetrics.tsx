/**
 * RAGAS 评估指标展示.
 *
 * 将 evaluation (Record<string, number>) 渲染为多维度彩色进度条,
 * 使用 getScoreColor 根据分值动态着色.
 */
import { Progress, Row, Col, Empty, Typography } from 'antd';
import { getScoreColor } from '@/utils';

const { Text } = Typography;

/** 已知 RAGAS 维度中文名映射. */
const METRIC_LABELS: Record<string, string> = {
  faithfulness: '忠实度',
  answerRelevancy: '答案相关性',
  contextPrecision: '上下文精确度',
  contextRecall: '上下文召回率',
};

interface EvaluationMetricsProps {
  /** 评估指标 (key -> 0~1 分值) */
  metrics: Record<string, number>;
}

export function EvaluationMetrics({ metrics }: EvaluationMetricsProps) {
  if (!metrics || Object.keys(metrics).length === 0) {
    return <Empty description="无评估指标" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  return (
    <Row gutter={[16, 16]}>
      {Object.entries(metrics).map(([key, value]) => {
        const pct = Math.round((Number(value) || 0) * 100);
        const label = METRIC_LABELS[key] ?? key;
        const color = getScoreColor(Number(value) || 0);
        return (
          <Col xs={24} sm={12} md={8} key={key}>
            <div style={{ marginBottom: 4 }}>
              <Text type="secondary">{label}</Text>
              <Text strong style={{ float: 'right', color }}>
                {pct}%
              </Text>
            </div>
            <Progress percent={pct} strokeColor={color} size="small" />
          </Col>
        );
      })}
    </Row>
  );
}

export default EvaluationMetrics;
