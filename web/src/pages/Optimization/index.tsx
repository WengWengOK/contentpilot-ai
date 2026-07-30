import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Alert,
  Table,
  Tag,
  Typography,
  Space,
  Empty,
  message,
} from 'antd';
import { SendOutlined, RocketOutlined, BulbOutlined, AimOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { OptimizeStrategy, PriorityAction, AgentResponse } from '@/types';
import { useOptimizeStrategy } from '@/hooks';
import { AgentResponseDisplay } from '@/components/AgentResponseDisplay';

const { Title, Paragraph, Text } = Typography;

const JSON_PLACEHOLDER = `{
  "totalPosts": 120,
  "totalViews": 580000,
  "avgEngagementRate": 0.042,
  "topTopics": [{ "name": "AI 内容运营", "views": 120000 }],
  "platformDistribution": [{ "name": "微信公众号", "value": 60 }]
}`;

const IMPACT_COLOR_MAP: Record<string, string> = {
  high: 'red',
  medium: 'orange',
  low: 'green',
};

const EFFORT_COLOR_MAP: Record<string, string> = {
  high: 'red',
  medium: 'orange',
  low: 'green',
};

export default function Optimization() {
  const [form] = Form.useForm();
  const [response, setResponse] = useState<AgentResponse | null>(null);

  const mutation = useOptimizeStrategy();

  const onFinish = async (values: { analysisData: string }) => {
    let parsed: Record<string, unknown>;
    try {
      parsed = values.analysisData ? JSON.parse(values.analysisData) : {};
    } catch {
      message.error('分析数据 JSON 格式不正确, 请检查');
      return;
    }
    try {
      const res = await mutation.mutateAsync({ analysisData: parsed });
      setResponse(res);
      message.success('优化策略生成成功');
    } catch {
      message.error('优化策略生成失败, 请重试');
    }
  };

  const strategy = response?.data as OptimizeStrategy | undefined;

  const columns: ColumnsType<PriorityAction> = [
    {
      title: '行动项',
      dataIndex: 'action',
      key: 'action',
      render: (val: string) => <Text>{val}</Text>,
    },
    {
      title: '预期影响',
      dataIndex: 'impact',
      key: 'impact',
      width: 120,
      render: (val: string) => (
        <Tag color={IMPACT_COLOR_MAP[val?.toLowerCase()] ?? 'default'}>
          {val === 'high' ? '高' : val === 'medium' ? '中' : val === 'low' ? '低' : val}
        </Tag>
      ),
    },
    {
      title: '实施成本',
      dataIndex: 'effort',
      key: 'effort',
      width: 120,
      render: (val: string) => (
        <Tag color={EFFORT_COLOR_MAP[val?.toLowerCase()] ?? 'default'}>
          {val === 'high' ? '高' : val === 'medium' ? '中' : val === 'low' ? '低' : val}
        </Tag>
      ),
    },
  ];

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        优化迭代
      </Title>
      <Paragraph type="secondary">
        由 optimize Agent 基于数据分析结论生成可执行的优化策略, 包含建议、优先级行动项与预期提升.
      </Paragraph>

      <Card title="分析数据" className="section-card">
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="分析数据 (JSON)"
            name="analysisData"
            tooltip="粘贴数据分析结果 (JSON), 由 optimize Agent 解读后生成优化策略"
            rules={[{ required: true, message: '请输入分析数据' }]}
          >
            <Input.TextArea
              rows={6}
              placeholder={JSON_PLACEHOLDER}
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={mutation.isPending}
              icon={<SendOutlined />}
            >
              生成优化策略
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <AgentResponseDisplay
        response={response}
        loading={mutation.isPending}
        title="优化策略结果"
        emptyDescription="提交分析数据后将展示 AI 生成的优化策略"
      >
        {!strategy ? (
          <Empty description="未获取到优化策略数据" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {strategy.recommendations && strategy.recommendations.length > 0 && (
              <div>
                <Title level={5}>
                  <BulbOutlined /> 优化建议
                </Title>
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  {strategy.recommendations.map((rec, idx) => (
                    <Alert
                      key={idx}
                      type="info"
                      showIcon
                      message={rec}
                      style={{ marginBottom: 0 }}
                    />
                  ))}
                </Space>
              </div>
            )}

            {strategy.priorityActions && strategy.priorityActions.length > 0 && (
              <div>
                <Title level={5}>
                  <AimOutlined /> 优先级行动项
                </Title>
                <Table<PriorityAction>
                  columns={columns}
                  dataSource={strategy.priorityActions.map((item, idx) => ({
                    ...item,
                    key: idx,
                  }))}
                  pagination={false}
                  size="middle"
                />
              </div>
            )}

            {strategy.expectedImprovement && (
              <Card
                size="small"
                style={{
                  background: 'linear-gradient(135deg, #e6f4ff 0%, #f6ffed 100%)',
                  border: '1px solid #d6e4ff',
                }}
              >
                <Space>
                  <RocketOutlined style={{ color: '#1677ff', fontSize: 18 }} />
                  <div>
                    <Text strong>预期提升: </Text>
                    <Text>{strategy.expectedImprovement}</Text>
                  </div>
                </Space>
              </Card>
            )}
          </Space>
        )}
      </AgentResponseDisplay>
    </div>
  );
}
