

---

## Entry 6 — Production Readiness, Multi-Tenancy, Grounded RAG & Security Remediation (Phases 0–10)
- **Date:** 2026-11-10
- **Summary of Changes:**
  - Completed Phase 0 Audit (`docs/audit/INITIAL_AUDIT.md`) and remediated all `P0`–`P3` findings.
  - Added Flyway migration `V5__multi_tenancy_rag_chunks_and_locking.sql` introducing `organisation_id` on `user_details` and `knowledge_articles`, `version` optimistic locking columns on `tickets` and `ai_approval_requests`, the `knowledge_chunks` table, and composite tenant/FK indexes.
  - Implemented `AccessControlService` and `CorrelationIdFilter`, enforced `@PreAuthorize` across all controllers, stripped internal agent notes from customer responses, added SHA-256 refresh token hashing, refresh token reuse family revocation, `POST /api/auth/logout`, and login brute-force rate limiting.
  - Extracted all nested controllers and `StorageService` into top-level classes; upgraded `KnowledgeService` with document chunking and minimum relevance threshold (`0.25`) to prevent fabricated citations; added `PromptInjectionGuard`, `AllowedAiTool` allowlist, and `LlmProviderClient`.
  - Modularized the React 19 frontend into component panels (`src/components/*`), expanded Vitest tests, added `frontend/nginx.conf`, `frontend/vercel.json`, `render.yaml`, and `application-prod.properties`, and produced all audit and operational documentation (`docs/audit/*.md`, `docs/SECURITY.md`, `docs/DEPLOYMENT.md`, `docs/OPERATIONS.md`, `docs/PERFORMANCE.md`, `docs/PROJECT_ROADMAP.md`).
