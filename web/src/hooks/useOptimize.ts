/**
 * 优化迭代 Hook.
 *
 * 封装 optimizeApi.strategy 为 TanStack Query mutation.
 */
import { useMutation } from '@tanstack/react-query';
import { optimizeApi } from '@/api/optimize';
import type { AgentResponse, OptimizeRequest } from '@/types';

/** 优化策略 (mutation, POST /api/v1/optimize/strategy). */
export function useOptimizeStrategy() {
  return useMutation<AgentResponse, Error, OptimizeRequest>({
    mutationKey: ['optimize', 'strategy'],
    mutationFn: optimizeApi.strategy,
  });
}
