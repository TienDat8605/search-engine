CREATE TABLE IF NOT EXISTS documents (
    question_id BIGINT UNIQUE,
    url VARCHAR(1024) PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    normalized_text TEXT,
    metadata_json TEXT,
    tags TEXT,
    question_text TEXT,
    best_answer_text TEXT,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Add embedding column if not exists (1024 dimensions for mistral-embed)
ALTER TABLE documents ADD COLUMN IF NOT EXISTS embedding vector(1024);

-- Create IVFFlat index for fast ANN search (only build when there are enough rows)
CREATE INDEX IF NOT EXISTS documents_embedding_idx ON documents USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

CREATE TABLE IF NOT EXISTS query_logs (
    id BIGSERIAL PRIMARY KEY,
    query_text VARCHAR(512) NOT NULL,
    sort VARCHAR(32) NOT NULL,
    tags TEXT,
    limit_value INT NOT NULL,
    offset_value INT NOT NULL,
    result_count INT NOT NULL,
    cache_hit BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS click_events (
    id BIGSERIAL PRIMARY KEY,
    query_text VARCHAR(512) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    position INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_click_events_query_url ON click_events(query_text, url, created_at DESC);

ALTER TABLE documents ADD COLUMN IF NOT EXISTS title_embedding vector(1024);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS answer_embedding vector(1024);
CREATE INDEX IF NOT EXISTS documents_title_embedding_idx ON documents USING ivfflat (title_embedding vector_cosine_ops) WITH (lists = 10);
CREATE INDEX IF NOT EXISTS documents_answer_embedding_idx ON documents USING ivfflat (answer_embedding vector_cosine_ops) WITH (lists = 10);
