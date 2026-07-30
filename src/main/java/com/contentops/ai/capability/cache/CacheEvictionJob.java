package com.contentops.ai.capability.cache;

import com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存清理定时任务。
 *
 * <p>每小时执行一次，清理 Qdrant {@code semantic_cache} collection 中
 * 已过期（expires_at &lt; now）的缓存条目。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionJob {

    private final QdrantVectorStoreService vectorStoreService;

    /**
     * 每小时清理一次过期缓存（fixedRate = 3600000ms）。
     */
    @Scheduled(fixedRate = 3600000)
    public void evictExpiredCache() {
        try {
            long now = System.currentTimeMillis();
            long deleted = vectorStoreService.deleteExpired(SemanticCacheService.COLLECTION, now);
            log.info("语义缓存清理完成, 删除过期条目数={}", deleted);
        } catch (Exception e) {
            log.error("语义缓存清理任务异常: {}", e.getMessage(), e);
        }
    }
}
