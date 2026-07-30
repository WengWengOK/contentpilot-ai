package com.contentops.ai.infrastructure.postgres;

import com.contentops.ai.domain.entity.KnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * RAG 知识文档 JPA 仓库.
 *
 * <p>供 {@code KnowledgeController} 上传知识文档时落库 PostgreSQL, 并与 Qdrant 向量点关联。</p>
 */
@Repository
public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, Long> {
}
