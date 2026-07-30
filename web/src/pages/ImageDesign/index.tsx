import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Select,
  Button,
  Image,
  Typography,
  Tag,
  Space,
  Empty,
  message,
} from 'antd';
import { SendOutlined, DownloadOutlined, PictureOutlined } from '@ant-design/icons';
import type { ImageResult, AgentResponse } from '@/types';
import { useImageGenerate } from '@/hooks';
import { IMAGE_STYLE_OPTIONS, IMAGE_STYLE_LABEL_MAP } from '@/constants';
import { AgentResponseDisplay } from '@/components/AgentResponseDisplay';

const { Title, Paragraph, Text } = Typography;

export default function ImageDesign() {
  const [form] = Form.useForm();
  const [response, setResponse] = useState<AgentResponse | null>(null);

  const mutation = useImageGenerate();

  const onFinish = async (values: { description: string; style: string }) => {
    try {
      const res = await mutation.mutateAsync({
        description: values.description,
        style: values.style,
      });
      setResponse(res);
      message.success('配图生成成功');
    } catch {
      message.error('配图生成失败, 请重试');
    }
  };

  const handleDownload = (url: string) => {
    const link = document.createElement('a');
    link.href = url;
    link.download = `contentpilot-image-${Date.now()}.png`;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const image = response?.data as ImageResult | undefined;

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        配图设计
      </Title>
      <Paragraph type="secondary">
        由 image_design Agent 将文本描述转化为 DALL-E 绘画 prompt 并生成配图, 失败时降级返回默认图.
      </Paragraph>

      <Card title="配图参数" className="section-card">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ style: 'realistic' }}
          onFinish={onFinish}
        >
          <Form.Item
            label="图片描述"
            name="description"
            rules={[{ required: true, message: '请输入图片描述' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder="请描述你想要的图片, 例如: 一座未来感十足的科技城市, 霓虹灯光, 赛博朋克风格"
              showCount
              maxLength={500}
            />
          </Form.Item>

          <Form.Item label="图片风格" name="style">
            <Select options={IMAGE_STYLE_OPTIONS} placeholder="请选择图片风格" style={{ width: 240 }} />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={mutation.isPending}
              icon={<SendOutlined />}
            >
              生成配图
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <AgentResponseDisplay
        response={response}
        loading={mutation.isPending}
        title="配图结果"
        emptyDescription="提交描述后将展示 AI 生成的配图"
      >
        {!image ? (
          <Empty description="未获取到配图数据" />
        ) : (
          <Card
            size="small"
            cover={
              <div style={{ textAlign: 'center', background: '#fafafa', padding: 16 }}>
                <Image
                  src={image.imageUrl}
                  alt={image.prompt}
                  style={{ maxWidth: '100%', maxHeight: 400, borderRadius: 8 }}
                  placeholder
                  fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                />
              </div>
            }
            actions={[
              <Button
                key="download"
                type="link"
                icon={<DownloadOutlined />}
                onClick={() => handleDownload(image.imageUrl)}
              >
                下载图片
              </Button>,
            ]}
          >
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              <Space size={[4, 4]} wrap>
                <Tag color="magenta">
                  <PictureOutlined /> {IMAGE_STYLE_LABEL_MAP[image.style] ?? image.style}
                </Tag>
              </Space>
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  生成 Prompt
                </Text>
                <Paragraph style={{ margin: '4px 0 0' }}>{image.prompt}</Paragraph>
              </div>
            </Space>
          </Card>
        )}
      </AgentResponseDisplay>
    </div>
  );
}
