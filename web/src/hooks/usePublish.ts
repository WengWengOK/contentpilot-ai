/**
 * 多平台发布 Hook.
 *
 * 封装 publishApi.multiPlatform 为 TanStack Query mutation.
 */
import { useMutation } from '@tanstack/react-query';
import { publishApi } from '@/api/publish';
import type { AgentResponse, PublishRequest } from '@/types';

/** 多平台发布 (mutation, POST /api/v1/publish/multi-platform). */
export function usePublishMultiPlatform() {
  return useMutation<AgentResponse, Error, PublishRequest>({
    mutationKey: ['publish', 'multi-platform'],
    mutationFn: publishApi.multiPlatform,
  });
}
