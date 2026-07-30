-- =============================================================================
-- V1__init_schema.sql
-- ContentOps AI 平台初始化表结构
-- 对齐系统设计文档 (SYSTEM_DESIGN.md §5.1) 定义的表结构:
--   tenant, app_user, knowledge_doc, agent_execution, agent_checkpoint,
--   ragas_evaluation, audit_log
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 租户表
-- -----------------------------------------------------------------------------
CREATE TABLE tenant (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_code  VARCHAR(64)  NOT NULL UNIQUE,
    tenant_name  VARCHAR(128) NOT NULL,
    daily_quota  INTEGER      NOT NULL DEFAULT 100000,  -- 每日 Token 配额
    status       VARCHAR(16)  NOT NULL DEFAULT 'active',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  tenant IS '多租户信息表';
COMMENT ON COLUMN tenant.daily_quota IS '每日 Token 配额上限';
COMMENT ON COLUMN tenant.status      IS '状态: active / disabled';

-- -----------------------------------------------------------------------------
-- 用户表
-- -----------------------------------------------------------------------------
CREATE TABLE app_user (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant(id),
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(256) NOT NULL,
    role        VARCHAR(32)  NOT NULL DEFAULT 'user',  -- admin / editor / viewer
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, username)
);
COMMENT ON TABLE  app_user IS '平台用户表';
CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);

-- -----------------------------------------------------------------------------
-- 知识文档表 (RAG 知识库, 含 PostgreSQL 全文索引用于 BM25)
-- -----------------------------------------------------------------------------
CREATE TABLE knowledge_doc (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES tenant(id),
    title      VARCHAR(256) NOT NULL,
    content    TEXT         NOT NULL,
    doc_type   VARCHAR(32)  NOT NULL DEFAULT 'article',
    -- PostgreSQL 全文索引向量列 (BM25 关键词检索基础)
    tsv        tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''))) STORED,
    -- Qdrant 中对应的向量 ID
    vector_id  VARCHAR(64),
    metadata   JSONB        NOT NULL DEFAULT '{}',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_doc_tsv    ON knowledge_doc USING GIN(tsv);
CREATE INDEX idx_doc_tenant ON knowledge_doc(tenant_id);
COMMENT ON TABLE  knowledge_doc IS 'RAG 知识文档表';
COMMENT ON COLUMN knowledge_doc.tsv       IS '全文检索向量列, 用于 BM25 关键词检索';
COMMENT ON COLUMN knowledge_doc.vector_id IS 'Qdrant 中对应的向量点 ID';

-- -----------------------------------------------------------------------------
-- Agent 执行记录表
-- -----------------------------------------------------------------------------
CREATE TABLE agent_execution (
    id            BIGSERIAL   PRIMARY KEY,
    execution_id  VARCHAR(64) NOT NULL UNIQUE,
    tenant_id     BIGINT      NOT NULL REFERENCES tenant(id),
    agent_type    VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'running',  -- running / completed / failed
    input         JSONB,
    output        JSONB,
    tokens_used   INTEGER     NOT NULL DEFAULT 0,
    model_used    VARCHAR(64),
    trace_id      VARCHAR(64),
    started_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMP
);
CREATE INDEX idx_agent_execution_tenant ON agent_execution(tenant_id);
CREATE INDEX idx_agent_execution_trace  ON agent_execution(trace_id);
CREATE INDEX idx_agent_execution_status ON agent_execution(status);
COMMENT ON TABLE  agent_execution IS 'Agent 执行记录表';
COMMENT ON COLUMN agent_execution.status IS '状态: running / completed / failed';

-- -----------------------------------------------------------------------------
-- Agent 检查点表 (断点续跑 / 状态快照)
-- -----------------------------------------------------------------------------
CREATE TABLE agent_checkpoint (
    id            BIGSERIAL    PRIMARY KEY,
    agent_id      VARCHAR(64)  NOT NULL,
    execution_id  VARCHAR(64)  NOT NULL,
    state         JSONB        NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_checkpoint_exec ON agent_checkpoint(execution_id, created_at DESC);
COMMENT ON TABLE agent_checkpoint IS 'Agent 执行检查点, 用于断点续跑与状态恢复';

-- -----------------------------------------------------------------------------
-- RAGAS 评估记录表
-- -----------------------------------------------------------------------------
CREATE TABLE ragas_evaluation (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL REFERENCES tenant(id),
    execution_id      VARCHAR(64),
    query             TEXT          NOT NULL,
    answer            TEXT,
    contexts          JSONB,
    faithfulness      DECIMAL(4,3),
    answer_relevancy  DECIMAL(4,3),
    context_precision DECIMAL(4,3),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ragas_evaluation_tenant ON ragas_evaluation(tenant_id);
CREATE INDEX idx_ragas_evaluation_exec   ON ragas_evaluation(execution_id);
COMMENT ON TABLE ragas_evaluation IS 'RAGAS 生成质量评估记录表';

-- -----------------------------------------------------------------------------
-- 审计日志表
-- -----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant(id),
    user_id     BIGINT,
    action      VARCHAR(64)  NOT NULL,
    resource    VARCHAR(128),
    detail      JSONB,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_time   ON audit_log(created_at);
COMMENT ON TABLE audit_log IS '操作审计日志表';
