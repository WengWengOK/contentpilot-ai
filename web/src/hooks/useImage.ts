/**
 * 配图设计 Hook.
 *
 * 封装 imageApi.generate 为 TanStack Query mutation.
 */
import { useMutation } from '@tanstack/react-query';
import { imageApi } from '@/api/image';
import type { AgentResponse, ImageGenerateRequest } from '@/types';

/** 配图生成 (mutation, POST /api/v1/image/generate). */
export function useImageGenerate() {
  return useMutation<AgentResponse, Error, ImageGenerateRequest>({
    mutationKey: ['image', 'generate'],
    mutationFn: imageApi.generate,
  });
}
