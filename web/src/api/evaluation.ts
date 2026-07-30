/**
 * 评估报告 API
 * 对应后端: GET /api/v1/evaluation/report
 */
import { get } from './client';
import type { RagasEvaluation } from '@/types';

export const evaluationApi = {
  /** 查询 RAGAS 评估报告 */
  report: (params: { startDate: string; endDate: string }) =>
    get<RagasEvaluation[]>('/evaluation/report', { params }),
};
