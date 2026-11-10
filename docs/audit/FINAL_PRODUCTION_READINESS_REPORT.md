# SupportFlow AI — Final Production Readiness & Release Candidate Report (`FINAL_PRODUCTION_READINESS_REPORT.md`)

**Report Date:** 2026-11-10  
**Repository:** `https://github.com/ashiksp1994/SupportFlow-AI`  
**Local Workspace:** `~/develop/SupportFlow-AI`  
**Target Hosting Profile:** Vercel Hobby (Frontend) + Render Free Web Service (Backend) + Neon Serverless PostgreSQL 17 with `pgvector` (Database) + Local Podman/Docker Compose.

---

## 1. Executive Summary

SupportFlow AI has undergone a complete 10-phase Git, Legacy Code, Secret, Application Security, Multi-Tenancy, Grounded AI/RAG, Backend, Frontend, Database, and Cloud Deployment audit and remediation. All remnants of the original Physiotherapy Clinic domain (`dateOfBirth`, `gender`, `ProfileTest`, legacy `"USER"` role tests, and duplicate Phase 1 docs) have been removed from the active codebase while preserving the historical reference branch `legacy/physiotherapy-clinic` locally.

---

## 2. Git Repository Health (`docs/audit/GIT_AUDIT.md`)

- **Active Branch:** `main` tracking `origin/main` (`https://github.com/ashiksp1994/SupportFlow-AI.git`).
- **Lineage Independence:** `git merge-base main legacy/physiotherapy-clinic` returns no common ancestor (`PASS` — clean orphan history on `main`).
- **Remotes:** `origin` (`SupportFlow-AI.git`), `upstream` (`DevOpsProject.git` with `push` set to `DISABLED`).
- **Tracked Build Cache Cleanup:** Removed `frontend/tsconfig.tsbuildinfo` from the Git index (`git rm --cached frontend/tsconfig.tsbuildinfo`).

---

## 3. Legacy Code Findings (`docs/audit/LEGACY_CODE_AUDIT.md`)

- Searched all tracked files for legacy Physiotherapy Clinic concepts (`physiotherapy`, `appointment`, `clinic`, `patient`, `therapist`, `treatment`, `consultation`, `booking`, `dateOfBirth`, `gender`, `appointment-service`, `token-service`, `cloud.sql`, `Jenkinsfile`, `cloudbuild.yaml`).
- Zero active Java or TypeScript source files reference any clinic, appointment, or patient domain concepts.

---

## 4. Removed Legacy Code (Approved & Executed in Stage 6)

1. **`UserModel.java`, `RegisterRequest.java`, `UserService.java`:** Removed legacy patient demographic fields `dateOfBirth` and `gender`.
2. **`V6__drop_legacy_patient_user_columns.sql`:** Added Flyway migration dropping `date_of_birth` and `gender` from `user_details`.
3. **`backend/src/test/java/com/supportflow/identity/ProfileTest.java`:** Deleted redundant legacy `ProfileTest`.
4. **`backend/src/test/java/com/supportflow/identity/UserModelTest.java`:** Replaced legacy `"USER"` role assertions with `UserRole.CUSTOMER`, `UserRole.ADMIN`, and `organisationId` assertions.
5. **`frontend/tsconfig.tsbuildinfo`, `docs/PROJECT_LOG.md`, `docs/ROADMAP.md`:** Untracked build cache file and removed duplicate markdown files superseded by `docs/DEVELOPMENT_LOG.md` and `docs/PROJECT_ROADMAP.md`.

---

## 5. Preserved Reusable Code

- **`com.supportflow.identity.*`:** Modernised authentication core (`AuthController`, `UserService`, `UserDetailsServiceImpl`, `JwtUtils`, `JwtRequestFilter`, `WebSecurityConfig`, `UserServiceIntegrationTest`) upgraded to Java 25, Spring Boot `4.1.2`, JJWT `0.12.6`, BCrypt, SHA-256 refresh token hashing, replay family revocation, `/api/auth/logout`, rate limiting, and `AccessControlService`.

---

## 6. Secret and Historical Credential Findings (`docs/audit/SECRET_AUDIT.md`)

- **Public `origin/main` Commit Graph:** **Zero real secrets or cloud credentials** exist in any commit on `main`.
- **Production Configuration Guardrails:** `application-prod.properties` has no fallback `JWT_SECRET`, and `JwtUtils` throws `IllegalStateException` on startup if `SPRING_PROFILES_ACTIVE=prod` is run with a dev fallback secret.
- **Demo Seeding Isolation:** `app.demo.seed-enabled` now defaults to `false` in `application.properties`, `application-prod.properties`, `AnalyticsService.java`, and `render.yaml`, and is enabled (`true`) only in `application-demo.properties` (`SPRING_PROFILES_ACTIVE=demo`) or when `APP_DEMO_SEED_ENABLED=true` is explicitly injected.
- **Local `legacy/physiotherapy-clinic` Branch:** Retains 2024 historical commits locally (`0ee47b5`, `c6ce415`, `503ba05`); never pushed to `origin`.

---

## 7. Security Audit (`docs/audit/SECURITY_AUDIT.md`)

- **Authentication:** BCrypt password hashing, 15-minute HMAC-SHA256 JWTs with issuer verification, SHA-256 hashed refresh tokens, single-use refresh rotation, token reuse family revocation (`SECURITY_TOKEN_REUSE_DETECTED`), `POST /api/auth/logout`, and per-username brute-force login rate limiting (`429 Too Many Requests`).
- **Authorization & Multi-Tenancy:** `@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")` on all staff endpoints; `AccessControlService.verifyTenantAccess` across all domain services; `internalNote == true` messages stripped from `CUSTOMER` responses; customer message spoofing blocked.

---

## 8. Backend Audit (`docs/audit/BACKEND_AUDIT.md`)

- **Spring Modulith:** 11 `@ApplicationModule` packages verified by `SupportFlowApplicationTests.verifiesModularStructure()`.
- **Clean Layering:** All REST controllers (`OrganisationController`, `CustomerController`, `TicketController`, `ConversationController`, `StorageController`, `KnowledgeController`, `AiController`, `AnalyticsController`, `AuditController`, `AuthController`) and `StorageService` are top-level classes.
- **Concurrency:** `@Version` optimistic locking on `Ticket` and `AiApprovalRequest`.
- **Observability:** `CorrelationIdFilter` binds `correlationId` to SLF4J `MDC` and returns `X-Correlation-Id` on every HTTP response.

---

## 9. Frontend Audit (`docs/audit/FRONTEND_AUDIT.md`)

- **Modular React 19 + TypeScript Architecture:** Extracted `AnalyticsOverviewPanel.tsx`, `ApprovalsGovernancePanel.tsx`, and `DirectoryAndAuditPanels.tsx`.
- **Session Lifecycle:** `logoutSession` calls `POST /api/auth/logout` and clears the TanStack Query cache.
- **Dependency Vulnerability Scan (`npm audit`):** Reported 4 advisories in `vitest` (`@vitest/mocker`/`tinypool` dev-only test runner) and `zod` (`<4.6.6`, where upgrading to `zod@4` is a major breaking change for `@hookform/resolvers@4.1.2`). Documented transparently without forced breaking upgrades.

---

## 10. Database Audit (`docs/audit/DATABASE_AUDIT.md`)

- **Flyway Migrations (`V1`–`V6`):** Verified against both H2 (`MODE=PostgreSQL`) and PostgreSQL 17 (`pgvector/pgvector:pg17` Testcontainer) with `spring.jpa.hibernate.ddl-auto=validate`.
- **Indexes:** Composite and foreign-key indexes on `user_details(organisation_id)`, `customers(organisation_id)`, `tickets(organisation_id, status, priority)`, `tickets(customer_id)`, `tickets(assigned_agent_username)`, `knowledge_articles(organisation_id, category)`, `knowledge_chunks(article_id)`, and `audit_logs(created_at)`.

---

## 11. AI and RAG Audit (`docs/audit/AI_AUDIT.md`)

- **Honest Implementation Classification:**
  - `LlmProviderClient` implements real HTTP calls (`java.net.http.HttpClient`, 8s timeout) to OpenAI-compatible `/chat/completions` endpoints when `AI_LLM_API_KEY` is provided, and falls back to the deterministic local grounded engine (`local-grounded`) when no API key is configured.
  - `KnowledgeService` segments articles into `KnowledgeChunk` records (`knowledge_chunks` table) and computes hybrid 12-dimension domain-concept cosine similarity + lexical overlap with a strict minimum relevance threshold (`0.25`).
  - **Hallucination Refusal:** When no chunk meets the `0.25` threshold, `AiOrchestrationService.suggestReplyWithCitations` returns `citations: []` and refuses to fabricate citations (`AI_RAG_INSUFFICIENT_EVIDENCE`).
  - **Prompt Injection & Tool Safety:** `PromptInjectionGuard` redacts injection sequences and blocks tool proposals (`400 Bad Request`); `AllowedAiTool` restricts tool names to 4 audited tools and requires human approval before execution.

---

## 12. Test Results

- **Backend (`cd backend && ./gradlew clean build check --no-daemon`):**
  - Unit, Security, Multi-Tenancy, Modulith & AI Tests (`:test`): **14 passed, 0 failed** (`ProfileTest` removed; `UserModelTest` modernised).
  - PostgreSQL 17 (`pgvector/pgvector:pg17`) Testcontainers Integration Tests (`:integrationTest`): **1 suite passed, 0 failed**.
  - JaCoCo Coverage Report (`backend/build/reports/jacoco/test/jacocoTestReport.xml`): **96.9% Class**, **74.8% Method**, **73.3% Instruction**, **72.8% Line**.
- **Frontend (`cd frontend && npm ci && npm test && npm run build`):**
  - Vitest (`src/test/App.test.tsx`): **4 passed, 0 failed**.
  - Production Bundle (`tsc -b && vite build`): **Succeeded (`0` errors)**.

---

## 13. Infrastructure Audit

- **Backend Container (`backend/Dockerfile`):** Multi-stage `eclipse-temurin:25-jdk-alpine` builder -> `eclipse-temurin:25-jre-alpine` non-root runtime (`USER supportflow`) with `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.
- **Frontend Container (`frontend/Dockerfile` & `frontend/nginx.conf`):** Multi-stage `node:22-alpine` (`npm ci && npm run build`) -> `nginx:1.27-alpine` with SPA fallback (`try_files $uri $uri/ /index.html`), `/api/` proxy, and security headers.
- **Local Compose (`docker-compose.yml`):** Core services (`postgres`, `backend`, `frontend`) run by default; optional `redis` and `minio` are under `profiles: ["full-infra"]`.

---

## 14. Hosting Readiness Matrix (Acceptance Criteria Verdicts)

| Acceptance Criterion | Verdict | Evidence |
| :--- | :---: | :--- |
| Clean independent Git history on `main` (`origin/main`) | **PASS** | `git merge-base main legacy/physiotherapy-clinic` returns no common ancestor (`docs/audit/GIT_AUDIT.md`). |
| Legacy Physiotherapy Clinic code removed from `main` | **PASS** | `LEG-001`..`LEG-005` removed; `V6__drop_legacy_patient_user_columns.sql` applied (`docs/audit/LEGACY_CODE_AUDIT.md`). |
| Zero exposed secrets on `origin/main` | **PASS** | Verified in `docs/audit/SECRET_AUDIT.md`. |
| JWT validation, SHA-256 refresh token hashing, rotation, reuse detection & logout | **PASS** | Verified by `ProductionReadinessSecurityAndAiTest.shouldHashRefreshTokensDetectTokenReuseRevokeOnLogoutAndEnforceLoginRateLimit`. |
| RBAC, multi-organisation tenant isolation & internal note hiding | **PASS** | Verified by `ProductionReadinessSecurityAndAiTest.shouldEnforceMultiTenantIsolationRbacAndHideInternalNotesFromCustomers`. |
| Grounded RAG chunking, minimum relevance cutoff & zero fabricated citations | **PASS** | Verified by `ProductionReadinessSecurityAndAiTest.shouldChunkKnowledgeArticlesRefuseUnverifiedCitationsAndBlockPromptInjectionAndUnknownTools`. |
| Prompt injection guardrails & human-in-the-loop AI tool approval | **PASS** | Verified by `PromptInjectionGuard`, `AllowedAiTool`, and `AiWorkflowsIntegrationTest`. |
| Demo seeding disabled by default outside `demo` profile | **PASS** | `app.demo.seed-enabled=false` in `application.properties`, `application-prod.properties`, and `AnalyticsService.java`. |
| Backend `./gradlew clean build check --no-daemon` | **PASS** | All unit, Modulith, and PostgreSQL 17 Testcontainers integration tests pass. |
| Frontend `npm ci && npm test && npm run build` | **PASS** | 4 Vitest tests and Vite production build pass. |
| Render, Vercel & Neon deployment manifests ready | **PASS** | `render.yaml`, `frontend/vercel.json`, `application-prod.properties`, `docs/DEPLOYMENT.md`. |
| Live external cloud deployment executed | **NOT VERIFIED** | Per Rule 11, external cloud resources were not modified or deployed without explicit user action. |

---

## 15. Outstanding Issues

1. **Frontend Transitive Advisories (`npm audit`):** `vitest@3.2.7` (`tinypool`/`@vitest/mocker` dev-only test dependencies) and `zod@3.24.2` (`GHSA-mrhx-c7xc-m3w5`) report advisories whose automated fix requires major version jumps (`vitest@5`, `zod@4`). Held back to preserve `@hookform/resolvers` compatibility.
2. **External LLM API Key Optionality:** Without an `AI_LLM_API_KEY` environment variable, `LlmProviderClient` uses its deterministic local grounded engine (`local-grounded`).

---

## 16. Production Limitations (€0/Month Free-Tier Architecture)

- **Render Free Web Service:** Sleeps after 15 minutes of inactivity (~30–50s cold start) and runs as a single 512 MiB instance.
- **Neon Free PostgreSQL:** Suspends compute after 5 minutes of inactivity (~500ms–2s wake time) and requires conservative connection pooling (`DB_POOL_MAX_SIZE=5`).
- **In-Memory Rate Limiting:** Login brute-force buckets are per-JVM instance (`ConcurrentHashMap`).

---

## 17. Deployment Instructions (Summary — Full Details in `docs/DEPLOYMENT.md`)

1. **Neon PostgreSQL:** Create a PostgreSQL 17 project in Neon and copy the SSL JDBC URL (`jdbc:postgresql://<ep>.neon.tech/neondb?sslmode=require`).
2. **Render Backend:** Deploy `render.yaml` (`backend/Dockerfile`), set `SPRING_PROFILES_ACTIVE=prod` (or `prod,demo` if demo seed data is desired), `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS`.
3. **Vercel Frontend:** Import `frontend/` in Vercel (`npm ci`, `npm run build`, output `dist`) and set `VITE_API_BASE_URL` to the Render service URL.

---

## 18. Rollback Instructions

1. **Application Rollback (Render / Vercel):**
   - In Render Dashboard -> **Events / Deploys**, click **Rollback to this deploy** on the previous green container image.
   - In Vercel Dashboard -> **Deployments**, promote the previous production deployment.
2. **Git Rollback:**
   - Revert the latest commit without rewriting history:
     ```bash
     git revert HEAD
     git push origin main
     ```
3. **Database Rollback (Neon):**
   - Use **Neon Instant Point-in-Time Restore (Branching)** in the Neon Console to restore the `main` database branch to the timestamp immediately preceding the migration.
