# SupportFlow AI — Product & Engineering Roadmap

This document tracks completed, in-progress, and planned milestones across the SupportFlow AI transformation.

---

## Phase 1: Backend Foundation & Modernisation (Completed)
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
- [x] Update `backend/Dockerfile` (multi-stage Java 25) and `docker-compose.yml` (`pgvector/pgvector:pg17`)

---

## Phase 2: Security & Authentication Hardening (Next Up)
- [ ] **Blocker (Issue #4):** Remove `allowForRefreshToken` authentication bypass in `JwtRequestFilter` and implement secure refresh token rotation
- [ ] **Blocker (Issue #5):** Replace `UserModel` entity binding in `AuthController` with validated request/response `record` DTOs (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse`), prevent password hash exposure, and block arbitrary role self-assignment
- [ ] **Blocker (Issue #6):** Map user roles (`ADMIN`, `AGENT`, `CUSTOMER`) to Spring Security `GrantedAuthority` in `UserDetailsServiceImpl` and enable `@EnableMethodSecurity`
- [ ] Replace Lombok `@Data` on `UserModel` JPA entity with explicit getters/setters and safe `id`-based equality
- [ ] Add global `@RestControllerAdvice` ProblemDetails (`RFC 9457`) exception handling (returning HTTP 401/400/409 instead of HTTP 500)
- [ ] Add `audit` module logging for authentication and security events
- [ ] Expand unit and MockMvc security slice tests covering registration, login, expired tokens, and RBAC enforcement

---

## Phase 3: Core Support Domain, Redis & S3 Storage (Planned)
- [ ] Customer and Organisation management (`organisations`, `customers` modules)
- [ ] Support Ticket lifecycle management, priority/status state machine (`tickets` module)
- [ ] Agent assignment and workload management
- [ ] Conversation and message management with internal notes vs. public replies (`conversations` module)
- [ ] SLA tracking and escalation rules
- [ ] Redis integration for caching and rate limiting
- [ ] S3-compatible object storage (MinIO locally) for ticket attachments (`storage` module)

---

## Phase 4: AI-Native Capabilities — Spring AI, RAG & Agent Workflows (Planned)
- [ ] Knowledge Base article & document management (`knowledge` module)
- [ ] Document chunking, embedding generation, and `pgvector` similarity search
- [ ] Provider-agnostic Spring AI configuration (OpenAI, Gemini, Ollama)
- [ ] Automated AI ticket classification, sentiment analysis, and urgency prioritisation
- [ ] RAG-powered customer response suggestions with verifiable source citations
- [ ] Conversation summarisation for agent handoffs
- [ ] Controlled AI tool calling with human-in-the-loop approval gates for consequential actions
- [ ] AI observability, token usage tracking, and evaluation tests

---

## Phase 5: React + TypeScript Frontend, Analytics & CI/CD (Planned)
- [ ] Scaffold `frontend/` with React, TypeScript, Vite, Tailwind CSS, shadcn/ui, TanStack Query, React Hook Form, and Zod
- [ ] Agent Workspace UI (Ticket queue, conversation view, AI Copilot sidebar with citations and approval actions)
- [ ] Knowledge Base & Admin Analytics dashboard (`analytics` module)
- [ ] Frontend unit tests (Vitest + React Testing Library) and E2E tests (Playwright)
- [ ] GitHub Actions CI/CD workflows (build, test, security scanning, container verification)
- [ ] OpenTelemetry tracing and structured JSON logging
