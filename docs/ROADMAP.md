# SupportFlow AI — Product & Engineering Roadmap

This document tracks completed milestones across the SupportFlow AI platform transformation.

---

## Phase 1: Backend Foundation & Modernisation (Completed — Commit `2c9c857`)
- [x] Full codebase and Git history security audit
- [x] Create clean orphan Git history and public GitHub repository (`ashiksp1994/SupportFlow-AI`)
- [x] Preserve legacy repository as push-disabled `upstream` remote
- [x] Restructure `user-service/` into `backend/` with `com.supportflow` modular monolith packages
- [x] Upgrade Gradle Wrapper from `8.8` to `9.9.0` with Foojay Java Toolchain auto-provisioning
- [x] Upgrade Java toolchain from `21` to **Java 25 LTS**
- [x] Upgrade Spring Boot from `3.3.4` to **Spring Boot 4.1.2** and add **Spring Modulith 2.1.2**
- [x] Convert WAR packaging to executable JAR and retire `ServletInitializer`
- [x] Remove obsolete `javax.servlet-api`, GCP `postgres-socket-factory`, and unused `spring-cloud-dependencies`
- [x] Restore valid `application.properties` with environment-driven secrets and add `.env.example`
- [x] Introduce Flyway schema migrations (`V1__init_user_schema.sql`) with `ddl-auto=validate`
- [x] Fix unit, Spring Modulith, and integration test suites (`./gradlew clean check`)

---

## Phase 2: Security & Authentication Hardening (Completed — Commit `b1df571`)
- [x] **Resolved Blocker (Issue #4):** Removed `allowForRefreshToken` authentication bypass in `JwtRequestFilter` and implemented persisted `RefreshToken` rotation (`POST /api/auth/refresh`)
- [x] **Resolved Blocker (Issue #5):** Replaced `UserModel` entity binding in `AuthController` with validated request/response `record` DTOs (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse`), prevented password hash exposure, and restricted self-registration to `CUSTOMER` role
- [x] **Resolved Blocker (Issue #6):** Mapped user roles (`ADMIN`, `AGENT`, `CUSTOMER`) to Spring Security `GrantedAuthority` in `UserDetailsServiceImpl` and enabled `@EnableMethodSecurity`
- [x] Replaced Lombok `@Data` on `UserModel` JPA entity with explicit `@Getter`/`@Setter` and safe `equals`/`hashCode`
- [x] Added global `@RestControllerAdvice` ProblemDetail (`RFC 9457`) exception handling
- [x] Implemented `audit` module (`AuditLog`, `AuditService`, `GET /api/audit-logs`) and `V2__security_and_audit_schema.sql`
- [x] Verified with `AuthSecurityIntegrationTest`

---

## Phase 3: Core Support Domain, Redis & S3 Storage (Completed — Commit `00a17eb`)
- [x] Customer and Organisation management (`organisations`, `customers` modules) with tiered SLAs (`ENTERPRISE`, `BUSINESS`, `STANDARD`)
- [x] Support Ticket lifecycle management, priority/status state machine (`tickets` module)
- [x] Agent assignment and active workload calculation (`/api/tickets/workload`)
- [x] Conversation and message management with internal notes vs. public replies (`conversations` module)
- [x] SLA tracking, due-date calculation, and event-driven escalation notifications (`notifications` module)
- [x] S3-compatible object storage (MinIO) metadata & presigned URL service for ticket attachments (`storage` module)
- [x] Added `V3__core_support_domain_schema.sql` and verified with `CoreSupportDomainTest`

---

## Phase 4: AI-Native Capabilities — RAG, Triage & Agent Workflows (Completed — Commit `8b4b363`)
- [x] Knowledge Base article & document management (`knowledge` module)
- [x] Semantic vector embedding computation and hybrid RAG similarity search (`/api/knowledge/search`)
- [x] Automated AI ticket classification, sentiment analysis, and urgency prioritisation (`POST /api/ai/tickets/{id}/triage`)
- [x] RAG-powered customer response suggestions with verifiable source citations (`POST /api/ai/tickets/{id}/suggest-reply`)
- [x] Conversation summarisation for agent handoffs (`POST /api/ai/tickets/{id}/summarize`)
- [x] Controlled AI tool calling with human-in-the-loop approval gates (`POST /api/ai/tickets/{id}/propose-action`, `POST /api/ai/approvals/{id}/decide`)
- [x] Added `V4__knowledge_and_ai_workflows_schema.sql` and verified with `AiWorkflowsIntegrationTest`

---

## Phase 5: Analytics, React + TypeScript Console & CI/CD (Completed)
- [x] Support Analytics & Reporting API (`analytics` module — `GET /api/analytics/dashboard`) and Enterprise Demo Seeder
- [x] React 19 + TypeScript + Vite + Tailwind CSS + TanStack Query + React Hook Form + Zod Frontend Console (`frontend/`)
- [x] Frontend unit tests with Vitest & React Testing Library (`frontend/src/test/App.test.tsx`)
- [x] GitHub Actions CI/CD workflow (`.github/workflows/ci.yml`)
- [x] Full Podman/Docker Compose orchestration (`docker-compose.yml` with `pgvector/pgvector:pg17`, `redis:7-alpine`, `minio/minio`, `backend`, and `frontend`)
