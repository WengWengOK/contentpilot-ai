/**
 * 配额查询 Hook.
 *
 * 封装 quotaApi.usage 为 TanStack Query, 每 30 秒自动刷新.
 */
import { useQuery } from '@tanstack/react-query';
import { quotaApi } from '@/api/quota';
import type { QuotaUsage } from '@/types';

/** 配额使用情况 (query, GET /api/v1/quota/usage, 每 30s 自动刷新). */
export function useQuotaUsage() {
  return useQuery<QuotaUsage, Error>({
    queryKey: ['quota', 'usage'],
    queryFn: quotaApi.usage,
    refetchInterval: 30 * 1000,
  });
}
