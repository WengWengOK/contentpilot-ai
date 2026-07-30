/**
 * 数据分析 Hook.
 *
 * 封装 analysisApi.monthly 为 TanStack Query, 仅在 startDate / endDate 均存在时启用.
 */
import { useQuery } from '@tanstack/react-query';
import { analysisApi } from '@/api/analysis';
import type { AgentResponse } from '@/types';

/**
 * 月度数据分析 (query, GET /api/v1/analysis/monthly).
 *
 * @param startDate 统计起始日期 (YYYY-MM-DD), 缺省时禁用查询.
 * @param endDate   统计结束日期 (YYYY-MM-DD), 缺省时禁用查询.
 */
export function useMonthlyAnalysis(startDate?: string, endDate?: string) {
  return useQuery<AgentResponse, Error>({
    queryKey: ['analysis', 'monthly', startDate, endDate],
    queryFn: () => analysisApi.monthly({ startDate: startDate!, endDate: endDate! }),
    enabled: !!startDate && !!endDate,
  });
}
