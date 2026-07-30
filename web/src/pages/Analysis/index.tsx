import { useState } from 'react';
import {
  Card,
  Form,
  DatePicker,
  Button,
  Statistic,
  Row,
  Col,
  Typography,
  Empty,
  Spin,
} from 'antd';
import { SearchOutlined, FileTextOutlined, EyeOutlined, RiseOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import dayjs, { type Dayjs } from 'dayjs';
import type { AnalysisData } from '@/types';
import { useMonthlyAnalysis } from '@/hooks';
import { PLATFORM_LABEL_MAP } from '@/constants';
import { formatNumber } from '@/utils';

const { RangePicker } = DatePicker;
const { Title, Paragraph } = Typography;

type RangeValue = [Dayjs | null, Dayjs | null] | null;

export default function Analysis() {
  const [form] = Form.useForm();
  const [dateRange, setDateRange] = useState<{ start: string; end: string } | null>(null);

  const { data: response, isLoading, isFetching } = useMonthlyAnalysis(
    dateRange?.start,
    dateRange?.end,
  );

  const analysis: AnalysisData | undefined = response?.data as AnalysisData | undefined;

  const onQuery = (values: { range: RangeValue }) => {
    const [start, end] = values.range ?? [null, null];
    if (!start || !end) return;
    setDateRange({
      start: start.format('YYYY-MM-DD'),
      end: end.format('YYYY-MM-DD'),
    });
  };

  const barOption = analysis
    ? {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 50, right: 20, top: 30, bottom: 40 },
        xAxis: {
          type: 'category',
          data: analysis.topTopics.map((t) => t.topic),
          axisLabel: { interval: 0, rotate: analysis.topTopics.length > 4 ? 20 : 0 },
        },
        yAxis: { type: 'value', name: '阅读量' },
        series: [
          {
            name: '阅读量',
            type: 'bar',
            data: analysis.topTopics.map((t) => t.views),
            itemStyle: { color: '#1677ff', borderRadius: [4, 4, 0, 0] },
          },
        ],
      }
    : null;

  const pieOption = analysis
    ? {
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0, left: 'center' },
        series: [
          {
            name: '平台分布',
            type: 'pie',
            radius: ['40%', '70%'],
            itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
            label: { show: false, position: 'center' },
            emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
            data: analysis.platformStats.map((p) => ({
              name: PLATFORM_LABEL_MAP[p.platform] ?? p.platform,
              value: p.posts,
            })),
          },
        ],
      }
    : null;

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        数据分析
      </Title>
      <Paragraph type="secondary">
        由 analysis Agent 查询统计周期内的内容运营数据, 生成月度分析报告与可视化图表.
      </Paragraph>

      <Card title="查询条件" className="section-card">
        <Form form={form} layout="inline" onFinish={onQuery}>
          <Form.Item
            label="统计周期"
            name="range"
            rules={[{ required: true, message: '请选择统计周期' }]}
          >
            <RangePicker
              ranges={{
                '近 7 天': [dayjs().subtract(7, 'day'), dayjs()],
                '近 30 天': [dayjs().subtract(30, 'day'), dayjs()],
                本月: [dayjs().startOf('month'), dayjs().endOf('month')],
              }}
              placeholder={['开始日期', '结束日期']}
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<SearchOutlined />}
              loading={isFetching}
            >
              查询分析
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {!dateRange ? (
        <Card className="section-card">
          <Empty description="请先选择统计周期并点击查询" />
        </Card>
      ) : isLoading ? (
        <Card className="section-card">
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <Spin tip="数据分析中..." size="large" />
          </div>
        </Card>
      ) : !analysis ? (
        <Card className="section-card">
          <Empty description="未查询到分析数据" />
        </Card>
      ) : (
        <>
          {dateRange && (
            <Paragraph type="secondary" style={{ marginBottom: 12 }}>
              统计周期: {dateRange.start} ~ {dateRange.end}
            </Paragraph>
          )}

          <Row gutter={[16, 16]}>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="发布内容总数"
                  value={formatNumber(analysis.totalPosts)}
                  prefix={<FileTextOutlined />}
                  valueStyle={{ color: '#1677ff' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="总阅读量"
                  value={formatNumber(analysis.totalViews)}
                  prefix={<EyeOutlined />}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="平均互动率"
                  value={analysis.avgEngagement * 100}
                  precision={2}
                  prefix={<RiseOutlined />}
                  valueStyle={{ color: '#fa8c16' }}
                  suffix="%"
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} md={14}>
              <Card title="热门选题阅读量 TOP" className="section-card">
                {barOption ? (
                  <ReactECharts option={barOption} style={{ height: 320 }} />
                ) : (
                  <Empty description="暂无数据" />
                )}
              </Card>
            </Col>
            <Col xs={24} md={10}>
              <Card title="平台分布" className="section-card">
                {pieOption ? (
                  <ReactECharts option={pieOption} style={{ height: 320 }} />
                ) : (
                  <Empty description="暂无数据" />
                )}
              </Card>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
}
