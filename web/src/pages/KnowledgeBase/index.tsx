import { useState } from 'react';
import {
  Row,
  Col,
  Card,
  Form,
  Input,
  Select,
  Button,
  List,
  Tag,
  Progress,
  Typography,
  Empty,
  Space,
  message,
  Spin,
} from 'antd';
import {
  UploadOutlined,
  FileSearchOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import { useKnowledgeUpload, useKnowledgeSearch } from '@/hooks';
import { DOC_TYPE_OPTIONS, DOC_TYPE_LABEL_MAP } from '@/constants';
import { getScoreColor } from '@/utils';

const { Title, Paragraph, Text } = Typography;

export default function KnowledgeBase() {
  const [uploadForm] = Form.useForm();
  const [searchQuery, setSearchQuery] = useState('');

  const uploadMutation = useKnowledgeUpload();
  const { data: results, isFetching: searchFetching } = useKnowledgeSearch(
    searchQuery,
    !!searchQuery,
  );

  const onUpload = async (values: {
    title: string;
    content: string;
    docType?: string;
  }) => {
    try {
      const res = await uploadMutation.mutateAsync({
        title: values.title,
        content: values.content,
        docType: values.docType ?? 'article',
      });
      message.success(`文档《${res.title}》上传成功${res.vectorized ? '并已向量化' : ''}`);
      uploadForm.resetFields();
    } catch {
      message.error('文档上传失败, 请重试');
    }
  };

  const onSearch = (query: string) => {
    if (!query || !query.trim()) {
      message.warning('请输入检索关键词');
      return;
    }
    setSearchQuery(query.trim());
  };

  const SOURCE_COLOR_MAP: Record<string, string> = {
    VECTOR: 'blue',
    BM25: 'green',
    FUSED: 'purple',
    RERANKED: 'magenta',
  };

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        知识库管理
      </Title>
      <Paragraph type="secondary">
        上传知识文档 (落库 + 向量化), 并通过混合检索 (向量 + BM25 + 重排) 查询相关知识.
      </Paragraph>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <Card
            title={
              <Space>
                <UploadOutlined />
                文档上传
              </Space>
            }
            className="section-card"
          >
            <Form
              form={uploadForm}
              layout="vertical"
              initialValues={{ docType: 'article' }}
              onFinish={onUpload}
            >
              <Form.Item
                label="文档标题"
                name="title"
                rules={[{ required: true, message: '请输入文档标题' }]}
              >
                <Input placeholder="请输入文档标题" maxLength={128} />
              </Form.Item>

              <Form.Item
                label="文档正文"
                name="content"
                rules={[{ required: true, message: '请输入文档正文' }]}
              >
                <Input.TextArea
                  rows={6}
                  placeholder="请输入文档正文内容..."
                  showCount
                  maxLength={10000}
                />
              </Form.Item>

              <Form.Item label="文档类型" name="docType">
                <Select options={DOC_TYPE_OPTIONS} placeholder="请选择文档类型" />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={uploadMutation.isPending}
                  icon={<UploadOutlined />}
                >
                  上传文档
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={14}>
          <Card
            title={
              <Space>
                <FileSearchOutlined />
                知识检索
              </Space>
            }
            className="section-card"
          >
            <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
              <Input.Search
                placeholder="输入关键词检索知识库..."
                enterButton={
                  <Button type="primary" loading={searchFetching}>
                    检索
                  </Button>
                }
                onSearch={onSearch}
                size="large"
              />
            </Space.Compact>

            {searchFetching ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <Spin tip="检索中..." />
              </div>
            ) : !searchQuery ? (
              <Empty description="请输入关键词进行检索" />
            ) : results && results.length === 0 ? (
              <Empty description="未检索到相关知识文档" />
            ) : (
              <List
                bordered
                dataSource={results ?? []}
                renderItem={(item, idx) => (
                  <List.Item>
                    <Space direction="vertical" size="small" style={{ width: '100%' }}>
                      <Space size={[4, 4]} wrap>
                        <Tag color="blue">#{idx + 1}</Tag>
                        {item.source && (
                          <Tag color={SOURCE_COLOR_MAP[item.source] ?? 'default'}>
                            {item.source}
                          </Tag>
                        )}
                        {(item.metadata as { docType?: string } | undefined)?.docType && (
                          <Tag color="geekblue">
                            {DOC_TYPE_LABEL_MAP[
                              (item.metadata as { docType: string }).docType
                            ] ?? (item.metadata as { docType: string }).docType}
                          </Tag>
                        )}
                      </Space>

                      <Paragraph
                        ellipsis={{ rows: 3 }}
                        style={{ margin: 0, color: 'rgba(0,0,0,0.85)' }}
                      >
                        {item.content}
                      </Paragraph>

                      <div style={{ maxWidth: 320 }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          相关度评分
                        </Text>
                        <Progress
                          percent={Math.round((item.score ?? 0) * 100)}
                          size="small"
                          strokeColor={getScoreColor(item.score)}
                          format={(p) => `${p}%`}
                        />
                      </div>
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <DatabaseOutlined />
            说明
          </Space>
        }
        size="small"
      >
        <Paragraph type="secondary" style={{ margin: 0 }}>
          上传的文档将先落库 PostgreSQL, 再向量化写入 Qdrant; 检索采用向量检索 +
          BM25 关键词检索的混合策略, 经 RRF 融合与 Cross-Encoder 重排后返回 TopK 结果.
        </Paragraph>
      </Card>
    </div>
  );
}
