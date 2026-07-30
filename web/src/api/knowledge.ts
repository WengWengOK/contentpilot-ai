/**
 * 知识库 API
 * 对应后端:
 *   POST /api/v1/knowledge/upload
 *   GET  /api/v1/knowledge/search
 */
import { get, post } from './client';
import type { KnowledgeSearchResult, KnowledgeUploadRequest, KnowledgeUploadResult } from '@/types';

export const knowledgeApi = {
  /** 上传知识文档 (落库 + 向量化) */
  upload: (data: KnowledgeUploadRequest) =>
    post<KnowledgeUploadResult>('/knowledge/upload', data),

  /** 知识库混合检索 */
  search: (params: { query: string; topK?: number }) =>
    get<KnowledgeSearchResult[]>('/knowledge/search', { params }),
};
