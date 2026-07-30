/**
 * 知识库 Hooks.
 *
 * - useKnowledgeUpload: 文档上传 (mutation).
 * - useKnowledgeSearch: 知识检索 (query, 按需启用).
 */
import { useMutation, useQuery } from '@tanstack/react-query';
import { knowledgeApi } from '@/api/knowledge';
import type {
  KnowledgeUploadRequest,
  KnowledgeUploadResult,
  KnowledgeSearchResult,
} from '@/types';

/** 知识文档上传 (mutation, POST /api/v1/knowledge/upload). */
export function useKnowledgeUpload() {
  return useMutation<KnowledgeUploadResult, Error, KnowledgeUploadRequest>({
    mutationKey: ['knowledge', 'upload'],
    mutationFn: knowledgeApi.upload,
  });
}

/**
 * 知识库检索 (query, GET /api/v1/knowledge/search).
 *
 * @param query   检索关键词, 为空时禁用查询.
 * @param enabled 是否启用查询 (默认 true, 配合 query 共同控制).
 */
export function useKnowledgeSearch(query: string, enabled = true) {
  return useQuery<KnowledgeSearchResult[], Error>({
    queryKey: ['knowledge', 'search', query],
    queryFn: () => knowledgeApi.search({ query }),
    enabled: enabled && !!query,
  });
}
