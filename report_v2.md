# 📘 Project Report

## Stack Overflow Semantic Search Engine (Spring Boot + Web Frontend)

**Sources:** Stack Overflow only (StackExchange API)
**Deployment target:** `https://tiendat.tech` (single VM)
**VM:** 2 vCPU, 4 GB RAM, 50 GB disk

---

## 1. Executive Summary

This project builds a coding-focused search website that retrieves and ranks Stack Overflow answers with a clean, modern UI. It progresses in three phases:

* **Phase 1:** Semantic-style search MVP (no vector DB yet) using StackExchange API + backend reranking and normalization.
* **Phase 2:** True semantic retrieval using **vector embeddings** and a vector store.
* **Phase 3:** AI-powered search using **RAG** to generate answers grounded in retrieved Stack Overflow content with citations.

The system is deployed as a public website on `tiendat.tech`, combining a frontend web app and a Spring Boot backend behind a reverse proxy with HTTPS.

---

## 2. Problem Statement

Developers need fast, high-quality answers. Stack Overflow is high-signal, but default search can be:

* Keyword dependent (miss paraphrases)
* Not optimized for “answer-first” reading
* Slow to browse multiple links and compare

The project solves this by providing:

* A unified search UI
* Answer-first results
* Increasingly semantic retrieval (embeddings)
* Optional AI-generated answer (RAG) with citations

---

## 3. Objectives

### Functional

* Search Stack Overflow questions and answers
* Show ranked results with useful snippets (especially accepted/top-voted answers)
* Support semantic retrieval (phase-based)
* Provide AI-generated answer in Phase 3 grounded in sources

### Non-Functional

* Run on a single low-cost VM
* Respect StackExchange API rate limits/backoff
* Stable latency via caching and controlled hydration
* Simple, modern frontend UX
* Extendable architecture (phased roadmap)

---

## 4. Product Scope & UX

### Target user

* Students, backend/frontend developers, interview prep learners, engineers troubleshooting errors

### Key frontend features

* Search bar with autosubmit on Enter
* Results list as cards: title, tags, score, snippet, “accepted” badge
* Filters: sorting (relevance/new), optionally tags/language
* “Open on Stack Overflow” link per result
* Loading skeleton, empty states, error handling
* Phase 3: “Ask AI” panel returning a generated answer with citations

---

## 5. System Architecture (Website + Backend)

### Phase 1 deployment architecture (single VM)

```
Browser (tiendat.tech)
   |
   | HTTPS
   v
Reverse Proxy (Caddy/Nginx)  ---- serves static frontend + routes API
   |                        \
   |                         \--> /api/* -> Spring Boot backend
   |
   +--> Frontend (static files: Next.js/Vite build)
   |
   +--> Spring Boot API (Search Service)
            |
            +--> Redis (cache)
            +--> Postgres (normalized docs)
            |
            +--> StackExchange API (Stack Overflow)
```

### Phase 2 adds

* Vector store service (Qdrant or pgvector)

### Phase 3 adds

* LLM provider (external API or self-hosted later)
* RAG pipeline endpoint `/api/ask`

---

## 6. Components

### 6.1 Frontend Web App (Modern UI)

**Recommended stack:** Next.js + Tailwind (or Vite + React + Tailwind)

Responsibilities:

* Provide `/` search page UI
* Call backend endpoints:

  * `GET /api/search?q=...`
  * `GET /api/doc/{id}` (optional)
  * `POST /api/ask` (Phase 3)
* Render results with client-side state, pagination, filters
* Display citations and links to original sources

Build output:

* Static assets served by reverse proxy (fast, simple)

---

### 6.2 Reverse Proxy (Caddy or Nginx)

Responsibilities:

* HTTPS termination (Let’s Encrypt)
* Route:

  * `/api/*` to Spring Boot container
  * everything else to frontend static files
* Optional: basic rate limiting per IP

---

### 6.3 Spring Boot Backend (Search API)

Endpoints:

* `GET /api/health`
* `GET /api/search?q=...&sort=...&tags=...`
* `GET /api/doc/{questionId}` (optional)
* `POST /api/ask` (Phase 3)

Responsibilities:

* Orchestrate retrieval and ranking
* Hydrate top results with accepted/top answers
* Cache results and store normalized docs
* (Phase 2) compute/store embeddings and query vector store
* (Phase 3) perform RAG answer generation

---

### 6.4 Redis (Caching)

* Search response cache: TTL 5–15 minutes
* Document cache: TTL 7–30 days
* Prevents API quota issues and speeds up repeated queries

---

### 6.5 Postgres (Persistent storage)

Stores only fetched documents, not the entire Stack Overflow corpus.

Suggested tables:

* `documents(question_id PK, title, url, tags, question_text, best_answer_text, metrics_json, fetched_at)`
* optional `query_logs(query, created_at)` and `rag_logs(...)`

---

## 7. Phase Roadmap

### Phase 1 — Semantic-Style Search MVP

Goal: deliver a useful website quickly.

Backend:

* Use StackExchange search (`/search` or `/search/advanced`) for candidates
* Hydrate top 5–10 to fetch accepted/top-voted answer bodies
* Normalize documents into internal schema
* Rerank using heuristics + semantic-like scoring (query normalization, expansions, tag inference)
* Cache aggressively

Frontend:

* Single-page search UI + results list
* Filters and good UX states
* Answer snippet emphasis

Deliverable:

* “Search → get high-quality answer-first results”

---

### Phase 2 — Vector Embeddings

Goal: true semantic retrieval.

Backend:

* Generate embeddings for stored docs (question + best answer)
* Store vectors in Qdrant or pgvector
* Query vector store to retrieve similar documents
* Hybrid retrieval:

  * candidate pool from local store / API
  * rerank by vector similarity + quality metrics

Frontend:

* Optional “Semantic mode” indicator
* “Related questions” panel (vector similarity)

Deliverable:

* Better matching for paraphrases and concept-based queries

---

### Phase 3 — AI-Powered Search (RAG)

Goal: generate a direct answer grounded in sources.

Backend:

* Retrieve top K documents via vector search
* Build context (snippets, code blocks)
* Call LLM to generate:

  * explanation steps
  * code snippet
  * common pitfalls
* Always cite sources (Stack Overflow links)
* Return an answer with citations and a confidence indicator

Frontend:

* “Ask AI” tab/panel
* Answer with citations and expandable source snippets

Deliverable:

* “Ask → get a grounded answer + references”

---

## 8. Workflow Details

### Search flow (Phase 1)

1. Frontend calls `GET /api/search?q=...`
2. Backend checks Redis search cache
3. If miss:

   * call StackExchange search
   * pick top N question IDs
   * hydrate top K answers with body (`filter=withbody`)
4. Normalize + store in Postgres; cache in Redis
5. Rerank and return results JSON
6. Frontend renders results cards + snippets

### Embedding flow (Phase 2)

* background job embeds newly stored documents
* vector store enables similarity retrieval

### RAG flow (Phase 3)

1. Frontend sends `POST /api/ask { query }`
2. Backend retrieves top K docs (vector)
3. LLM generates answer grounded in those docs
4. Return answer + citations to frontend

---

## 9. Deployment Plan (tiendat.tech)

### Single VM services via Docker Compose

* `proxy` (Caddy/Nginx)
* `frontend` (optional; can be served directly by proxy from build artifacts)
* `api` (Spring Boot)
* `redis`
* `postgres`
* (Phase 2) `qdrant` (optional)

### DNS + HTTPS

* A record for `tiendat.tech` → VM IP
* Reverse proxy automatically handles TLS certificates

---

## 10. Resource Plan (2 vCPU / 4GB RAM)

### Memory budget (safe)

* Spring Boot: 1 GB heap (`-Xmx1024m`)
* Postgres: ~1–1.2 GB
* Redis: 256–512 MB maxmemory
* OS + overhead: ~1 GB

### Performance guardrails

* Hydrate only top 5–10 results per query
* Strict HTTP timeouts (3–5s)
* Limit outbound concurrency
* Cache everything possible

---

## 11. Risks & Mitigations

| Risk                        | Impact          | Mitigation                                 |
| --------------------------- | --------------- | ------------------------------------------ |
| StackExchange quota/backoff | degraded search | caching + API key + respect backoff        |
| Slow external responses     | high latency    | strict timeouts + async enrichment         |
| Memory pressure on 4GB      | instability     | container memory caps + small thread pools |
| RAG hallucination (Phase 3) | wrong answers   | citations + refusal when context weak      |

---

## 12. Conclusion

This project delivers a complete search website on `tiendat.tech` with a phased evolution:

* Phase 1 proves product value with fast “semantic-style” retrieval and strong UX
* Phase 2 upgrades retrieval quality with true embeddings
* Phase 3 provides an AI answer experience grounded in retrieved Stack Overflow sources

The system stays lightweight, deployable on a single VM, and grows in capability without requiring a full web archive.
