# SupportFlow AI — Performance, Scalability & Cost Engineering (`docs/PERFORMANCE.md`)

## 1. Database Query & Indexing Profile

- **Composite & Foreign-Key Indexes (`V3`, `V4`, `V5` migrations):**
  - `idx_tickets_org_status_priority` on `tickets(organisation_id, status, priority)` accelerates tenant-scoped queue filtering.
  - `idx_tickets_assigned_agent` on `tickets(assigned_agent_username)` supports agent workload calculation.
  - `idx_kb_articles_org_category` and `idx_kb_chunks_article_id` accelerate RAG chunk lookup.
  - `idx_audit_logs_created_at` accelerates recent audit log retrieval (`findTop100ByOrderByCreatedAtDesc`).
- **Connection Pool Sizing (`application-prod.properties`):**
  - Tuned for serverless PostgreSQL (`DB_POOL_MAX_SIZE=5`, `DB_POOL_MIN_IDLE=1`, `max-lifetime=600000`) to avoid exhausting Neon free-tier connection limits while keeping memory footprint under Render's 512 MiB RAM ceiling (`-XX:MaxRAMPercentage=75.0` in `backend/Dockerfile`).

## 2. AI Latency, Timeout & Token Cost Controls

- **Bounded Timeouts:** `LlmProviderClient` enforces an 8-second connect and request timeout (`app.ai.llm.timeout-seconds=8`) and caps `max_tokens` at `500`.
- **Zero-Cost Deterministic Fallback:** When `AI_LLM_API_KEY` is omitted, `LlmProviderClient` executes in `local-grounded` mode (`< 5 ms` latency, `€0.00` token cost) while still enforcing full `KnowledgeService` chunk retrieval and citation thresholds.
- **Chunk-Level RAG Retrieval:** `KnowledgeService` segments articles into `<= 360` character chunks (`knowledge_chunks`), avoiding full-document prompt bloat.

## 3. Frontend Bundle Profile

- **Verified Production Build (`npm run build`):**
  - CSS bundle: `23.83 kB` (`4.92 kB` gzipped)
  - JS bundle: `397.36 kB` (`116.61 kB` gzipped)
