/**
 * 选题策划 Hook.
 *
 * 封装 topicApi.suggest 为 TanStack Query mutation.
 */
import { useMutation } from '@tanstack/react-query';
import { topicApi } from '@/api/topic';
import type { AgentResponse, TopicSuggestRequest } from '@/types';

/** 选题建议 (mutation, POST /api/v1/topic/suggest). */
export function useTopicSuggest() {
  return useMutation<AgentResponse, Error, TopicSuggestRequest>({
    mutationKey: ['topic', 'suggest'],
    mutationFn: topicApi.suggest,
  });
}
