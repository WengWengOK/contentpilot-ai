import { useState } from 'react';
import {
  Card,
  Form,
  Select,
  InputNumber,
  Button,
  Tag,
  Progress,
  List,
  Typography,
  Empty,
  Space,
  message,
} from 'antd';
import { SendOutlined, BulbOutlined } from '@ant-design/icons';
import type { TopicSuggestion, AgentResponse } from '@/types';
import { useTopicSuggest } from '@/hooks';
import { PLATFORM_OPTIONS } from '@/constants';
import { getScoreColor } from '@/utils';
import { AgentResponseDisplay } from '@/components/AgentResponseDisplay';

const { Title, Paragraph, Text } = Typography;

export default function TopicPlanning() {
  const [form] = Form.useForm();
  const [response, setResponse] = useState<AgentResponse | null>(null);

  const mutation = useTopicSuggest();

  const onFinish = async (values: {
    keywords: string[];
    platform?: string;
    count: number;
  }) => {
    if (!values.keywords || values.keywords.length === 0) {
      message.warning('请输入至少一个关键词');
      return;
    }
    try {
      const res = await mutation.mutateAsync({
        keywords: values.keywords,
        platform: values.platform ?? 'wechat',
        count: values.count,
      });
      setResponse(res);
      message.success('选题建议生成成功');
    } catch {
      message.error('选题建议生成失败, 请重试');
    }
  };

  const suggestions = (response?.data as TopicSuggestion[] | undefined) ?? [];

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        选题策划
      </Title>
      <Paragraph type="secondary">
        基于关键词与目标平台, 由 topic_planning Agent 智能生成高热度选题建议.
      </Paragraph>

      <Card title="选题参数" className="section-card">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ platform: 'wechat', count: 5 }}
          onFinish={onFinish}
        >
          <Form.Item
            label="关键词"
            name="keywords"
            tooltip="输入关键词后回车添加, 支持多个关键词"
            rules={[{ required: true, message: '请输入至少一个关键词' }]}
          >
            <Select
              mode="tags"
              placeholder="输入关键词后回车, 例如: AI、内容运营、增长"
              tokenSeparators={[',', ' ']}
            />
          </Form.Item>

          <Space size="large" wrap>
            <Form.Item label="目标平台" name="platform" style={{ minWidth: 200 }}>
              <Select options={PLATFORM_OPTIONS} placeholder="请选择目标平台" />
            </Form.Item>

            <Form.Item
              label="选题数量"
              name="count"
              rules={[{ required: true, message: '请输入选题数量' }]}
            >
              <InputNumber min={1} max={10} precision={0} style={{ width: 160 }} />
            </Form.Item>
          </Space>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={mutation.isPending}
              icon={<SendOutlined />}
            >
              生成选题建议
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <AgentResponseDisplay
        response={response}
        loading={mutation.isPending}
        title="选题建议结果"
        emptyDescription="提交关键词后将展示 AI 生成的选题建议"
      >
        {suggestions.length === 0 ? (
          <Empty description="本次未生成任何选题" />
        ) : (
          <List
            grid={{ gutter: 16, xs: 1, sm: 1, md: 2, lg: 2, xl: 2 }}
            dataSource={suggestions}
            renderItem={(item, idx) => (
              <List.Item>
                <Card
                  size="small"
                  title={
                    <Space>
                      <BulbOutlined style={{ color: '#faad14' }} />
                      <Title level={5} style={{ margin: 0 }}>
                        {item.title}
                      </Title>
                    </Space>
                  }
                  extra={<Tag color="blue">#{idx + 1}</Tag>}
                >
                  <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ marginBottom: 12 }}>
                    {item.summary}
                  </Paragraph>

                  <Space size={[4, 4]} wrap style={{ marginBottom: 12 }}>
                    {(item.keywords ?? []).map((kw) => (
                      <Tag key={kw}>{kw}</Tag>
                    ))}
                    {item.category && <Tag color="geekblue">{item.category}</Tag>}
                  </Space>

                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      热度评分
                    </Text>
                    <Progress
                      percent={Math.round((item.trendingScore ?? 0) * 100)}
                      size="small"
                      strokeColor={getScoreColor(item.trendingScore ?? 0)}
                      format={(p) => `${p}%`}
                    />
                  </div>
                </Card>
              </List.Item>
            )}
          />
        )}
      </AgentResponseDisplay>
    </div>
  );
}
