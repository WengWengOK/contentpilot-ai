import { useMemo } from 'react';
import { Row, Col, Card, Statistic, Table, Tag, Typography, Spin } from 'antd';
import {
  RobotOutlined,
  ThunderboltOutlined,
  GoldOutlined,
  PercentageOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { useQuotaUsage } from '@/hooks';
import { formatNumber, formatDateTime } from '@/utils';

const { Title, Paragraph } = Typography;

/** 最近活动记录. */
interface ActivityRecord {
  key: string;
  time: string;
  agent: string;
  action: string;
  status: 'completed' | 'running' | 'failed';
  traceId: string;
}

/** Agent 标识 -> 中文名. */
const AGENT_LABEL_MAP: Record<string, string> = {
  topic_planning: '选题策划',
  content_creation: '内容创作',
  image_design: '配图设计',
  publish: '排版发布',
  analysis: '数据分析',
  optimize: '优化迭代',
};

/** Agent 分布 mock 数据. */
const AGENT_DISTRIBUTION = [
  { name: '选题策划', value: 18 },
  { name: '内容创作', value: 32 },
  { name: '配图设计', value: 24 },
  { name: '排版发布', value: 15 },
  { name: '数据分析', value: 8 },
  { name: '优化迭代', value: 12 },
];

/** 最近活动 mock 数据. */
const RECENT_ACTIVITIES: ActivityRecord[] = [
  {
    key: '1',
    time: dayjs().subtract(3, 'minute').toISOString(),
    agent: 'topic_planning',
    action: '生成选题建议',
    status: 'completed',
    traceId: 'a1b2c3d4e5f6',
  },
  {
    key: '2',
    time: dayjs().subtract(12, 'minute').toISOString(),
    agent: 'content_creation',
    action: '生成内容大纲',
    status: 'completed',
    traceId: 'b2c3d4e5f6g7',
  },
  {
    key: '3',
    time: dayjs().subtract(28, 'minute').toISOString(),
    agent: 'image_design',
    action: '生成配图',
    status: 'completed',
    traceId: 'c3d4e5f6g7h8',
  },
  {
    key: '4',
    time: dayjs().subtract(45, 'minute').toISOString(),
    agent: 'publish',
    action: '多平台发布',
    status: 'failed',
    traceId: 'd4e5f6g7h8i9',
  },
  {
    key: '5',
    time: dayjs().subtract(70, 'minute').toISOString(),
    agent: 'analysis',
    action: '月度数据分析',
    status: 'completed',
    traceId: 'e5f6g7h8i9j0',
  },
];

const STATUS_TAG_MAP: Record<ActivityRecord['status'], { color: string; text: string }> = {
  completed: { color: 'green', text: '已完成' },
  running: { color: 'processing', text: '执行中' },
  failed: { color: 'red', text: '失败' },
};

/** Agent 分布饼图配置. */
function useAgentPieOption() {
  return useMemo(
    () => ({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, left: 'center' },
      series: [
        {
          name: 'Agent 分布',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false, position: 'center' },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
          labelLine: { show: false },
          data: AGENT_DISTRIBUTION,
        },
      ],
    }),
    [],
  );
}

/** 近 7 日 Token 用量趋势折线图配置. */
function useTokenTrendOption() {
  return useMemo(() => {
    const today = dayjs();
    const days = Array.from({ length: 7 }, (_, i) =>
      today.subtract(6 - i, 'day').format('MM-DD'),
    );
    const values = [8200, 9300, 7600, 12100, 10800, 13500, 9700];
    return {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: days, boundaryGap: false },
      yAxis: { type: 'value', name: 'Tokens' },
      series: [
        {
          name: 'Token 用量',
          type: 'line',
          smooth: true,
          data: values,
          areaStyle: { opacity: 0.15 },
          itemStyle: { color: '#1677ff' },
        },
      ],
    };
  }, []);
}

export default function Dashboard() {
  const { data: quota, isLoading: quotaLoading } = useQuotaUsage();

  const pieOption = useAgentPieOption();
  const trendOption = useTokenTrendOption();

  const columns: ColumnsType<ActivityRecord> = [
    {
      title: '时间',
      dataIndex: 'time',
      key: 'time',
      width: 180,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: 'Agent',
      dataIndex: 'agent',
      key: 'agent',
      width: 140,
      render: (val: string) => AGENT_LABEL_MAP[val] ?? val,
    },
    { title: '动作', dataIndex: 'action', key: 'action' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (val: ActivityRecord['status']) => {
        const conf = STATUS_TAG_MAP[val];
        return <Tag color={conf.color}>{conf.text}</Tag>;
      },
    },
    {
      title: 'TraceId',
      dataIndex: 'traceId',
      key: 'traceId',
      width: 160,
      render: (val: string) => <Tag color="purple">{val}</Tag>,
    },
  ];

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        控制台
      </Title>
      <Paragraph type="secondary">ContentPilot AI 平台运行概览与实时状态.</Paragraph>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Agent 总数"
              value={6}
              prefix={<RobotOutlined />}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="活跃工作流"
              value={4}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#52c41a' }}
              suffix="/ 6"
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            {quotaLoading ? (
              <Spin />
            ) : (
              <Statistic
                title="今日 Token 用量"
                value={formatNumber(quota?.used ?? 0)}
                prefix={<GoldOutlined />}
                valueStyle={{ color: '#fa8c16' }}
                suffix={`/ ${formatNumber(quota?.dailyQuota ?? 0)}`}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="缓存命中率"
              value={68.5}
              precision={1}
              prefix={<PercentageOutlined />}
              valueStyle={{ color: '#722ed1' }}
              suffix="%"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={12}>
          <Card title="Agent 执行分布" className="section-card">
            <ReactECharts option={pieOption} style={{ height: 320 }} />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="近 7 日 Token 用量趋势" className="section-card">
            <ReactECharts option={trendOption} style={{ height: 320 }} />
          </Card>
        </Col>
      </Row>

      <Card title="最近活动" className="section-card">
        <Table<ActivityRecord>
          columns={columns}
          dataSource={RECENT_ACTIVITIES}
          pagination={{ pageSize: 5, size: 'small' }}
          size="middle"
        />
      </Card>
    </div>
  );
}
