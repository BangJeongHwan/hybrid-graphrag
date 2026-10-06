-- 설계서 3.1. Aurora PostgreSQL 16 + pgvector 0.8+
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS rag;

CREATE TABLE IF NOT EXISTS rag.doc_chunk (
  chunk_id      text PRIMARY KEY,
  doc_id        text NOT NULL,
  doc_type      text NOT NULL,
  title         text NOT NULL,
  section_path  text[] NOT NULL DEFAULT '{}',
  service_ids   text[] NOT NULL,
  category      text,
  entity_ids    text[] NOT NULL DEFAULT '{}',
  issue_id      text,
  audience      text NOT NULL,
  status        text NOT NULL DEFAULT 'ACTIVE',
  lang          text NOT NULL DEFAULT 'ko',
  content       text NOT NULL,
  content_hash  text NOT NULL,
  kg_version    bigint NOT NULL DEFAULT 0,
  updated_at    timestamptz NOT NULL DEFAULT now(),
  valid_until   timestamptz,
  embedding     vector(1024) NOT NULL,
  lexical       tsvector NOT NULL
);

CREATE INDEX IF NOT EXISTS doc_chunk_embedding_hnsw ON rag.doc_chunk
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX IF NOT EXISTS doc_chunk_lexical_gin  ON rag.doc_chunk USING gin (lexical);
CREATE INDEX IF NOT EXISTS doc_chunk_service_gin  ON rag.doc_chunk USING gin (service_ids);
CREATE INDEX IF NOT EXISTS doc_chunk_entity_gin   ON rag.doc_chunk USING gin (entity_ids);
CREATE INDEX IF NOT EXISTS doc_chunk_filter_btree ON rag.doc_chunk (status, audience);
