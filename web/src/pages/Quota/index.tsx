import {
  Card,
  Progress,
  Statistic,
  Row,
  Col,
  Typography,
  Alert,
  Descriptions,
  Spin,
  Space,
} from 'antd';
import {
  WalletOutlined,
  FireOutlined,
  RestOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useQuotaUsage } from '@/hooks';
import { useAppStore } from '@/store/useAppStore';
import { formatNumber } from '@/utils';

const { Title, Paragraph, Text } = Typography;

export default function Quota() {
  const tenantId = useAppStore((s) => s.tenantId);
  const { data: quota, isLoading } = useQuotaUsage();

  const dailyQuota = quota?.dailyQuota ?? 0;
  const used = quota?.used ?? 0;
  const remaining = quota?.remaining ?? 0;
  const usagePct = dailyQuota > 0 ? Math.round((used / dailyQuota) * 100) : 0;
  const isOverThreshold = usagePct > 80;
  const isExhausted = dailyQuota > 0 && remaining <= 0;

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        配额管理
      </Title>
      <Paragraph type="secondary">
        查看当前租户的每日 Token 配额使用情况, 数据每 30 秒自动刷新.
      </Paragraph>

      {isLoading ? (
        <Card className="section-card">
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <Spin tip="加载配额数据..." size="large" />
          </div>
        </Card>
      ) : (
        <>
          {isOverThreshold && (
            <Alert
              style={{ marginBottom: 16 }}
              type={isExhausted ? 'error' : 'warning'}
              showIcon
              icon={<WarningOutlined />}
              message={
                isExhausted
                  ? '今日配额已耗尽, 请联系管理员提升配额或等待次日重置'
                  : `今日 Token 用量已达 ${usagePct}%, 接近配额上限, 请合理规划调用`
              }
              description={`已使用 ${formatNumber(used)} / ${formatNumber(dailyQuota)} Tokens`}
            />
          )}

          <Row gutter={[16, 16]}>
            <Col xs={24} md={10}>
              <Card title="用量概览" className="section-card">
                <div style={{ textAlign: 'center', padding: '16px 0' }}>
                  <Progress
                    type="circle"
                    percent={Math.min(usagePct, 100)}
                    size={220}
                    strokeColor={
                      usagePct >= 90 ? '#f5222d' : usagePct > 80 ? '#faad14' : '#52c41a'
                    }
                    format={(p) => (
                      <span>
                        <div style={{ fontSize: 28, fontWeight: 600 }}>{p}%</div>
                        <Text type="secondary" style={{ fontSize: 13 }}>
                          已使用
                        </Text>
                      </span>
                    )}
                  />
                  <Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
                    {formatNumber(used)} / {formatNumber(dailyQuota)} Tokens
                  </Paragraph>
                </div>
              </Card>
            </Col>

            <Col xs={24} md={14}>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Row gutter={[16, 16]}>
                  <Col xs={24} sm={8}>
                    <Card>
                      <Statistic
                        title="每日配额"
                        value={formatNumber(dailyQuota)}
                        prefix={<WalletOutlined />}
                        valueStyle={{ color: '#1677ff' }}
                        suffix="Tokens"
                      />
                    </Card>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Card>
                      <Statistic
                        title="今日已用"
                        value={formatNumber(used)}
                        prefix={<FireOutlined />}
                        valueStyle={{ color: '#fa541c' }}
                        suffix="Tokens"
                      />
                    </Card>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Card>
                      <Statistic
                        title="今日剩余"
                        value={formatNumber(remaining)}
                        prefix={<RestOutlined />}
                        valueStyle={{ color: '#52c41a' }}
                        suffix="Tokens"
                      />
                    </Card>
                  </Col>
                </Row>

                <Card title="租户信息" className="section-card">
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label="租户标识">
                      <Text code>{quota?.tenantId ?? tenantId}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="配额周期">每日 (00:00 重置)</Descriptions.Item>
                    <Descriptions.Item label="当前用量百分比">
                      <Text
                        strong
                        style={{
                          color:
                            usagePct >= 90 ? '#f5222d' : usagePct > 80 ? '#faad14' : '#52c41a',
                        }}
                      >
                        {usagePct}%
                      </Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="刷新策略">
                      每 30 秒自动刷新
                    </Descriptions.Item>
                  </Descriptions>
                </Card>
              </Space>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
}
