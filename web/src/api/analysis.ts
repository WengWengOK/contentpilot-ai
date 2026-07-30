/**
 * 数据分析 API
 * 对应后端: GET /api/v1/analysis/monthly
 */
import { get } from './client';
import type { AgentResponse } from '@/types';

export const analysisApi = {
  /** 月度数据分析 */
  monthly: (params: { startDate: string; endDate: string }) =>
    get<AgentResponse>(`/analysis/monthly`, { params }),
};
