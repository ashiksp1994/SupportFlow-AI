# SupportFlow AI — Project Roadmap & Engineering Evolution (`docs/PROJECT_ROADMAP.md`)

## Completed Engineering Milestones

- **Phase 1 — Foundation & Modular Monolith Consolidation:**
  - Replaced legacy microservices prototype with Java 25 LTS, Spring Boot `4.1.2`, Spring Modulith `2.1.2`, Gradle `9.9.0`, and Flyway `V1`–`V5`.
- **Phase 2 — Authentication, RBAC & Multi-Tenancy Hardening:**
  - Implemented BCrypt, HMAC-SHA256 JWTs, SHA-256 hashed refresh tokens with replay family revocation, `POST /api/auth/logout`, login rate limiting, and `AccessControlService` tenant isolation.
- **Phase 3 — Core Support Domain, SLAs & Omnichannel Conversations:**
  - Organisations, VIP customer profiles, SLA breach tracking, agent workload metrics, internal agent note isolation, and S3 attachment metadata validation.
- **Phase 4 — Grounded RAG, Chunking, Prompt Injection Guardrails & Human Tool Governance:**
  - Document chunking (`knowledge_chunks`), minimum relevance cutoff (`0.25`) preventing citation fabrication, `PromptInjectionGuard`, `AllowedAiTool` allowlist, `LlmProviderClient`, and `@Version` optimistic locking on approvals.
- **Phase 5 — React 19 Enterprise Console, Containerisation & Cloud Blueprints:**
  - Modular React 19 + TypeScript + Tailwind CSS v4 + TanStack Query console, multi-stage Dockerfiles with Nginx SPA config, GitHub Actions CI, and Render/Vercel/Neon deployment manifests.

---

## Future Production Enhancements (Post-Free-Tier Evolution)

1. **Native `pgvector` HNSW Index & Dense Embedding Provider:**
   - Migrate `knowledge_chunks.embedding_vector` from `TEXT` to `vector(1536)` with an `HNSW` index (`vector_cosine_ops`) when running exclusively on PostgreSQL (`pgvector`) without H2 unit-test sharing.
2. **Distributed Rate Limiting & Caching via Upstash Redis:**
   - Move `UserService` login rate-limit buckets and KB search caching to Redis when scaling beyond a single Render backend replica.
3. **Direct S3 / Cloudflare R2 Presigned PUT Uploads:**
   - Wire AWS S3 SDK v2 (`S3Presigner`) against Cloudflare R2 / MinIO for direct browser-to-R2 binary uploads.
4. **End-to-End Playwright Browser Automation:**
   - Add headless Playwright E2E tests alongside existing Vitest and Spring Boot / Testcontainers suites.
