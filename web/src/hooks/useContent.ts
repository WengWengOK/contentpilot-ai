/**
 * 内容创作 Hook.
 *
 * 封装 contentApi.create 为 TanStack Query mutation.
 */
import { useMutation } from '@tanstack/react-query';
import { contentApi } from '@/api/content';
import type { AgentResponse, ContentCreateRequest } from '@/types';

/** 内容创作 (mutation, POST /api/v1/content/create). */
export function useContentCreate() {
  return useMutation<AgentResponse, Error, ContentCreateRequest>({
    mutationKey: ['content', 'create'],
    mutationFn: contentApi.create,
  });
}
