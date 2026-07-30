import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Checkbox,
  Button,
  List,
  Tag,
  Typography,
  Space,
  Result,
  Empty,
  message,
} from 'antd';
import {
  SendOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  LinkOutlined,
} from '@ant-design/icons';
import type { PublishResult, AgentResponse } from '@/types';
import { usePublishMultiPlatform } from '@/hooks';
import { PLATFORM_OPTIONS, PLATFORM_LABEL_MAP } from '@/constants';
import { AgentResponseDisplay } from '@/components/AgentResponseDisplay';

const { Title, Paragraph, Text } = Typography;

export default function Publishing() {
  const [form] = Form.useForm();
  const [response, setResponse] = useState<AgentResponse | null>(null);

  const mutation = usePublishMultiPlatform();

  const onFinish = async (values: { content: string; platforms: string[] }) => {
    if (!values.platforms || values.platforms.length === 0) {
      message.warning('请至少选择一个目标平台');
      return;
    }
    try {
      const res = await mutation.mutateAsync({
        content: values.content,
        platforms: values.platforms,
      });
      setResponse(res);
      message.success('多平台发布任务已完成');
    } catch {
      message.error('发布失败, 请重试');
    }
  };

  const platforms = (response?.data as PublishResult[] | undefined) ?? [];
  const successCount = platforms.filter((p) => p.success).length;
  const totalCount = platforms.length;
  const allSuccess = totalCount > 0 && successCount === totalCount;

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        多平台发布
      </Title>
      <Paragraph type="secondary">
        由 publish Agent 将内容一键分发到多个平台, 返回各平台发布结果与访问链接.
      </Paragraph>

      <Card title="发布参数" className="section-card">
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="发布内容"
            name="content"
            rules={[{ required: true, message: '请输入发布内容' }]}
          >
            <Input.TextArea
              rows={6}
              placeholder="请输入需要发布的内容..."
              showCount
              maxLength={5000}
            />
          </Form.Item>

          <Form.Item
            label="目标平台"
            name="platforms"
            rules={[{ required: true, message: '请至少选择一个目标平台' }]}
          >
            <Checkbox.Group options={PLATFORM_OPTIONS} />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={mutation.isPending}
              icon={<SendOutlined />}
            >
              发布到多平台
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <AgentResponseDisplay
        response={response}
        loading={mutation.isPending}
        title="发布结果"
        emptyDescription="提交内容后将展示各平台发布结果"
      >
        {platforms.length === 0 ? (
          <Empty description="未获取到发布结果" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Result
              status={allSuccess ? 'success' : 'warning'}
              icon={allSuccess ? <CheckCircleFilled /> : undefined}
              title={`${successCount} / ${totalCount} 个平台发布成功`}
              subTitle={
                allSuccess
                  ? '所有目标平台均已成功发布'
                  : `${totalCount - successCount} 个平台发布失败, 请查看下方详情`
              }
            />

            <List
              bordered
              dataSource={platforms}
              renderItem={(item) => {
                const platformLabel = PLATFORM_LABEL_MAP[item.platform] ?? item.platform;
                return (
                  <List.Item>
                    <Space
                      style={{ width: '100%', justifyContent: 'space-between' }}
                      wrap
                    >
                      <Space size="middle" wrap>
                        <Text strong>{platformLabel}</Text>
                        <Tag color={item.success ? 'green' : 'red'}>
                          {item.success ? (
                            <>
                              <CheckCircleFilled /> 发布成功
                            </>
                          ) : (
                            <>
                              <CloseCircleFilled /> 发布失败
                            </>
                          )}
                        </Tag>
                      </Space>
                      <Space direction="vertical" size={0} style={{ alignItems: 'flex-end' }}>
                        {item.url ? (
                          <a href={item.url} target="_blank" rel="noopener noreferrer">
                            <LinkOutlined /> 查看发布链接
                          </a>
                        ) : null}
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {item.message || (item.success ? '发布成功' : '发布失败')}
                        </Text>
                      </Space>
                    </Space>
                  </List.Item>
                );
              }}
            />
          </Space>
        )}
      </AgentResponseDisplay>
    </div>
  );
}
