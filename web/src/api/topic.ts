/**
 * 选题策划 API
 * 对应后端: POST /api/v1/topic/suggest
 */
import { post } from './client';
import type { AgentResponse, TopicSuggestRequest } from '@/types';

export const topicApi = {
  /** 生成选题建议 */
  suggest: (data: TopicSuggestRequest) => post<AgentResponse>('/topic/suggest', data),
};
