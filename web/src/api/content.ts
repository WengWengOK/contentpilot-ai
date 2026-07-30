/**
 * 内容创作 API
 * 对应后端: POST /api/v1/content/create
 */
import { post } from './client';
import type { AgentResponse, ContentCreateRequest } from '@/types';

export const contentApi = {
  /** 生成内容大纲 */
  create: (data: ContentCreateRequest) => post<AgentResponse>('/content/create', data),
};
