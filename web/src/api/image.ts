/**
 * 配图设计 API
 * 对应后端: POST /api/v1/image/generate
 */
import { post } from './client';
import type { AgentResponse, ImageGenerateRequest } from '@/types';

export const imageApi = {
  /** 生成配图 */
  generate: (data: ImageGenerateRequest) => post<AgentResponse>('/image/generate', data),
};
