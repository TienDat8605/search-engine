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
