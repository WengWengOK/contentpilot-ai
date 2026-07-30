/**
 * 多平台发布 API
 * 对应后端: POST /api/v1/publish/multi-platform
 */
import { post } from './client';
import type { AgentResponse, PublishRequest } from '@/types';

export const publishApi = {
  /** 多平台发布 */
  multiPlatform: (data: PublishRequest) =>
    post<AgentResponse>('/publish/multi-platform', data),
};
