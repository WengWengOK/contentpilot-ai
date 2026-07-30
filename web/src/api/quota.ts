/**
 * 配额查询 API
 * 对应后端: GET /api/v1/quota/usage
 */
import { get } from './client';
import type { QuotaUsage } from '@/types';

export const quotaApi = {
  /** 查询当前租户配额使用情况 */
  usage: () => get<QuotaUsage>('/quota/usage'),
};
