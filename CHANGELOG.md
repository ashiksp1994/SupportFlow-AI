# Changelog

All notable changes to **SupportFlow AI** are documented in this file.

---

## [1.0.0] — 2026-11-10

### Added
- **Phase 5 — Analytics, React + TypeScript Console & CI/CD:**
  - Implemented `com.supportflow.analytics` module (`GET /api/analytics/dashboard`) and realistic Enterprise Demo Data Seeder (`application-demo.properties`).
  - Built `frontend/` React 19 + TypeScript + Vite + Tailwind CSS + TanStack Query + React Hook Form + Zod console covering Executive Analytics, Ticket & AI Copilot Workspace, Human-in-the-Loop Tool Approval Center, Knowledge Base RAG Explorer, Customer/Organisation Directory, and Immutable Audit Log Viewer.
  - Added Vitest + React Testing Library frontend test suite (`App.test.tsx`).
  - Added GitHub Actions CI pipeline (`.github/workflows/ci.yml`) and full-stack `docker-compose.yml` (PostgreSQL `pgvector`, Redis, MinIO, Backend, Frontend).
- **Phase 4 — Knowledge Base RAG & Controlled AI Agent Workflows (`8b4b363`):**
  - Implemented `com.supportflow.knowledge` module (`KnowledgeArticle`, semantic vector embeddings, hybrid RAG search `/api/knowledge/search`).
  - Implemented `com.supportflow.ai` module (`AiOrchestrationService` for ticket triage, RAG response suggestions with citations, conversation summarisation, and controlled AI tool calling with human approval gates `/api/ai/approvals`).
  - Added Flyway migration `V4__knowledge_and_ai_workflows_schema.sql` and `AiWorkflowsIntegrationTest`.
- **Phase 3 — Core Customer Support Domain (`00a17eb`):**
  - Implemented `organisations`, `customers`, `tickets`, `conversations`, `storage`, and `notifications` modules.
  - Added Flyway migration `V3__core_support_domain_schema.sql` and `CoreSupportDomainTest`.
- **Phase 2 — Security & Authentication Hardening (`b1df571`):**
  - Remediated `JwtRequestFilter` refresh token authentication bypass (Issue `#4`) and added persisted `RefreshToken` rotation (`POST /api/auth/refresh`).
  - Replaced JPA entity binding in `AuthController` with validated `record` DTOs, prevented password hash leakage and privilege escalation (Issue `#5`).
  - Mapped `UserRole` (`CUSTOMER`, `AGENT`, `ADMIN`) to Spring Security `GrantedAuthority` in `UserDetailsServiceImpl` and enabled `@EnableMethodSecurity` (Issue `#6`).
  - Implemented `com.supportflow.audit` module (`AuditLog`, `AuditService`, `GET /api/audit-logs`), `V2__security_and_audit_schema.sql`, and `AuthSecurityIntegrationTest`.
- **Phase 1 — Foundation & Clean Repository Initialization (`2c9c857`):**
  - Initialized clean orphan Git history on `https://github.com/ashiksp1994/SupportFlow-AI`.
  - Restructured `user-service/` into `backend/` modular monolith on Java 25 LTS, Gradle 9.9.0, Spring Boot 4.1.2, Spring Modulith 2.1.2, and Flyway (`V1__init_user_schema.sql`).
