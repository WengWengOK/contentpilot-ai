/**
 * 优化迭代 API
 * 对应后端: POST /api/v1/optimize/strategy
 */
import { post } from './client';
import type { AgentResponse, OptimizeRequest } from '@/types';

export const optimizeApi = {
  /** 生成优化策略 */
  strategy: (data: OptimizeRequest) => post<AgentResponse>('/optimize/strategy', data),
};
