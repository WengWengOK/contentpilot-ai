/**
 * 评估报告 Hook.
 *
 * 封装 evaluationApi.report 为 TanStack Query, 仅在 startDate / endDate 均存在时启用.
 */
import { useQuery } from '@tanstack/react-query';
import { evaluationApi } from '@/api/evaluation';
import type { RagasEvaluation } from '@/types';

/**
 * RAGAS 评估报告 (query, GET /api/v1/evaluation/report).
 *
 * @param startDate 统计起始日期 (YYYY-MM-DD), 缺省时禁用查询.
 * @param endDate   统计结束日期 (YYYY-MM-DD), 缺省时禁用查询.
 */
export function useEvaluationReport(startDate?: string, endDate?: string) {
  return useQuery<RagasEvaluation[], Error>({
    queryKey: ['evaluation', 'report', startDate, endDate],
    queryFn: () => evaluationApi.report({ startDate: startDate!, endDate: endDate! }),
    enabled: !!startDate && !!endDate,
  });
}
