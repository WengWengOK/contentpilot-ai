import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Select,
  Button,
  Collapse,
  List,
  Typography,
  Divider,
  Space,
  message,
} from 'antd';
import { SendOutlined, FileTextOutlined, CheckCircleOutlined } from '@ant-design/icons';
import type { ContentOutline, AgentResponse } from '@/types';
import { useContentCreate } from '@/hooks';
import { PLATFORM_OPTIONS } from '@/constants';
import { AgentResponseDisplay } from '@/components/AgentResponseDisplay';

const { Title, Paragraph, Text } = Typography;

export default function ContentCreation() {
  const [form] = Form.useForm();
  const [response, setResponse] = useState<AgentResponse | null>(null);

  const mutation = useContentCreate();

  const onFinish = async (values: {
    topic: string;
    keywords?: string[];
    platform?: string;
  }) => {
    try {
      const res = await mutation.mutateAsync({
        topic: values.topic,
        keywords: values.keywords,
        platform: values.platform,
      });
      setResponse(res);
      message.success('内容大纲生成成功');
    } catch {
      message.error('内容大纲生成失败, 请重试');
    }
  };

  const outline = response?.data as ContentOutline | undefined;

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        内容创作
      </Title>
      <Paragraph type="secondary">
        由 content_creation Agent 根据选题与关键词生成结构化内容大纲, 包含引言、章节要点与结语.
      </Paragraph>

      <Card title="创作参数" className="section-card">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ platform: 'wechat' }}
          onFinish={onFinish}
        >
          <Form.Item
            label="选题标题"
            name="topic"
            rules={[{ required: true, message: '请输入选题标题' }]}
          >
            <Input placeholder="请输入选题标题, 例如: 2024 企业 AI 内容运营实战指南" />
          </Form.Item>

          <Space size="large" wrap>
            <Form.Item label="关键词" name="keywords" style={{ minWidth: 320 }}>
              <Select
                mode="tags"
                placeholder="输入关键词后回车"
                tokenSeparators={[',', ' ']}
                style={{ minWidth: 320 }}
              />
            </Form.Item>

            <Form.Item label="目标平台" name="platform">
              <Select options={PLATFORM_OPTIONS} placeholder="请选择目标平台" style={{ width: 200 }} />
            </Form.Item>
          </Space>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={mutation.isPending}
              icon={<SendOutlined />}
            >
              生成内容大纲
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <AgentResponseDisplay
        response={response}
        loading={mutation.isPending}
        title="内容大纲结果"
        emptyDescription="提交选题后将展示 AI 生成的内容大纲"
      >
        {!outline ? (
          <Text type="secondary">未获取到内容大纲数据</Text>
        ) : (
          <div>
            <Title level={4}>
              <FileTextOutlined /> {outline.title}
            </Title>

            {outline.introduction && (
              <>
                <Divider orientation="left" orientationMargin={0}>
                  引言
                </Divider>
                <Paragraph>{outline.introduction}</Paragraph>
              </>
            )}

            {outline.sections && outline.sections.length > 0 && (
              <>
                <Divider orientation="left" orientationMargin={0}>
                  正文章节
                </Divider>
                <Collapse
                  defaultActiveKey={outline.sections.map((_, idx) => String(idx))}
                  items={outline.sections.map((section, idx) => ({
                    key: String(idx),
                    label: (
                      <Space>
                        <Text strong>{section.order ?? idx + 1}.</Text>
                        <Text strong>{section.heading}</Text>
                      </Space>
                    ),
                    children: (
                      <List
                        size="small"
                        bordered
                        dataSource={section.bulletPoints ?? []}
                        renderItem={(point) => (
                          <List.Item>
                            <Text>{point}</Text>
                          </List.Item>
                        )}
                        locale={{ emptyText: '暂无要点' }}
                      />
                    ),
                  }))}
                />
              </>
            )}

            {outline.conclusion && (
              <>
                <Divider orientation="left" orientationMargin={0}>
                  结语
                </Divider>
                <Paragraph>
                  <CheckCircleOutlined style={{ color: '#52c41a', marginRight: 6 }} />
                  {outline.conclusion}
                </Paragraph>
              </>
            )}
          </div>
        )}
      </AgentResponseDisplay>
    </div>
  );
}
