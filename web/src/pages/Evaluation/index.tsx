import { useMemo, useState } from 'react';
import {
  Card,
  Form,
  DatePicker,
  Button,
  Statistic,
  Table,
  Progress,
  Typography,
  Empty,
  Spin,
  Row,
  Col,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import dayjs, { type Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import type { RagasEvaluation } from '@/types';
import { useEvaluationReport } from '@/hooks';
import { formatDateTime, getScoreColor } from '@/utils';

const { RangePicker } = DatePicker;
const { Title, Paragraph } = Typography;

type RangeValue = [Dayjs | null, Dayjs | null] | null;

export default function Evaluation() {
  const [form] = Form.useForm();
  const [dateRange, setDateRange] = useState<{ start: string; end: string } | null>(null);

  const { data: records, isLoading, isFetching } = useEvaluationReport(
    dateRange?.start,
    dateRange?.end,
  );

  const list: RagasEvaluation[] = records ?? [];

  const averages = useMemo(() => {
    if (list.length === 0) {
      return { faithfulness: 0, answerRelevancy: 0, contextPrecision: 0 };
    }
    const sum = list.reduce(
      (acc, r) => ({
        faithfulness: acc.faithfulness + (r.faithfulness ?? 0),
        answerRelevancy: acc.answerRelevancy + (r.answerRelevancy ?? 0),
        contextPrecision: acc.contextPrecision + (r.contextPrecision ?? 0),
      }),
      { faithfulness: 0, answerRelevancy: 0, contextPrecision: 0 },
    );
    return {
      faithfulness: sum.faithfulness / list.length,
      answerRelevancy: sum.answerRelevancy / list.length,
      contextPrecision: sum.contextPrecision / list.length,
    };
  }, [list]);

  const radarOption = useMemo(
    () => ({
      tooltip: {},
      radar: {
        indicator: [
          { name: '忠实度', max: 1 },
          { name: '答案相关性', max: 1 },
          { name: '上下文精确度', max: 1 },
        ],
        radius: '65%',
        axisName: { color: '#666' },
        splitArea: { areaStyle: { color: ['#fafafa', '#f0f2f5'] } },
      },
      series: [
        {
          name: 'RAGAS 平均指标',
          type: 'radar',
          areaStyle: { opacity: 0.2 },
          lineStyle: { color: '#1677ff' },
          itemStyle: { color: '#1677ff' },
          data: [
            {
              value: [
                averages.faithfulness,
                averages.answerRelevancy,
                averages.contextPrecision,
              ],
              name: '平均值',
            },
          ],
        },
      ],
    }),
    [averages],
  );

  const onQuery = (values: { range: RangeValue }) => {
    const [start, end] = values.range ?? [null, null];
    if (!start || !end) return;
    setDateRange({
      start: start.format('YYYY-MM-DD'),
      end: end.format('YYYY-MM-DD'),
    });
  };

  const columns: ColumnsType<RagasEvaluation> = [
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: '执行 ID',
      dataIndex: 'executionId',
      key: 'executionId',
      width: 150,
      render: (val?: string) =>
        val ? (
          <Typography.Text style={{ fontSize: 12 }} code>
            {val.length > 16 ? `${val.slice(0, 14)}…` : val}
          </Typography.Text>
        ) : (
          '-'
        ),
    },
    {
      title: '查询',
      dataIndex: 'query',
      key: 'query',
      render: (val: string) => (
        <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>
          {val}
        </Typography.Paragraph>
      ),
    },
    {
      title: '忠实度',
      dataIndex: 'faithfulness',
      key: 'faithfulness',
      width: 140,
      render: (val: number) => (
        <Progress
          percent={Math.round((val ?? 0) * 100)}
          size="small"
          strokeColor={getScoreColor(val)}
          format={(p) => `${p}%`}
        />
      ),
    },
    {
      title: '答案相关性',
      dataIndex: 'answerRelevancy',
      key: 'answerRelevancy',
      width: 140,
      render: (val: number) => (
        <Progress
          percent={Math.round((val ?? 0) * 100)}
          size="small"
          strokeColor={getScoreColor(val)}
          format={(p) => `${p}%`}
        />
      ),
    },
    {
      title: '上下文精确度',
      dataIndex: 'contextPrecision',
      key: 'contextPrecision',
      width: 140,
      render: (val: number) => (
        <Progress
          percent={Math.round((val ?? 0) * 100)}
          size="small"
          strokeColor={getScoreColor(val)}
          format={(p) => `${p}%`}
        />
      ),
    },
  ];

  return (
    <div className="page-container">
      <Title level={3} className="page-title">
        RAGAS 评估报告
      </Title>
      <Paragraph type="secondary">
        查看 RAGAS 生成质量评估记录, 包含忠实度 (faithfulness)、答案相关性 (answer
        relevancy)、上下文精确度 (context precision) 三大维度.
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
              查询报告
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
            <Spin tip="加载评估报告..." size="large" />
          </div>
        </Card>
      ) : list.length === 0 ? (
        <Card className="section-card">
          <Empty description="所选周期内暂无评估记录" />
        </Card>
      ) : (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="平均忠实度"
                  value={averages.faithfulness * 100}
                  precision={2}
                  valueStyle={{ color: getScoreColor(averages.faithfulness) }}
                  suffix="%"
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="平均答案相关性"
                  value={averages.answerRelevancy * 100}
                  precision={2}
                  valueStyle={{ color: getScoreColor(averages.answerRelevancy) }}
                  suffix="%"
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card>
                <Statistic
                  title="平均上下文精确度"
                  value={averages.contextPrecision * 100}
                  precision={2}
                  valueStyle={{ color: getScoreColor(averages.contextPrecision) }}
                  suffix="%"
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} md={10}>
              <Card title="三维评估雷达图" className="section-card">
                <ReactECharts option={radarOption} style={{ height: 320 }} />
              </Card>
            </Col>
            <Col xs={24} md={14}>
              <Card title={`评估记录 (${list.length} 条)`} className="section-card">
                <Table<RagasEvaluation>
                  columns={columns}
                  dataSource={list.map((r) => ({ ...r, key: r.id }))}
                  size="middle"
                  pagination={{ pageSize: 8, size: 'small' }}
                  scroll={{ x: 900 }}
                />
              </Card>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
}
