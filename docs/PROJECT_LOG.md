# SupportFlow AI — Project Development Log

This document maintains a chronological engineering log of all development sessions, architectural changes, test executions, problems discovered, and next steps.

---

## Session 1 — 2026-11-10: Codebase Audit, Clean Repository Initialization & Phase 1 Backend Modernisation

- **Date:** 2026-11-10
- **Phase:** Audit & Phase 1 — Stabilise the Backend Foundation
- **Objective:**
  1. Perform a full forensic audit of the legacy Physiotherapy Clinic repository.
  2. Establish a clean, secret-free Git history and public GitHub repository (`ashiksp1994/SupportFlow-AI`) while preserving the legacy repository as a push-disabled `upstream` reference.
  3. Restructure the backend into a modular monolith (`backend/`, package `com.supportflow`) running on **Java 25 LTS**, **Gradle 9.9.0**, **Spring Boot 4.1.2**, **Spring Modulith 2.1.2**, and **Flyway**.
  4. Ensure all unit, module-verification, and integration tests build and pass cleanly.

### Work Completed
1. **Repository & Security Audit:**
   - Inspected all working tree files and Git history (`503ba05`, `6025704`, `0ee47b5`, `origin/master`).
   - Identified plaintext Cloud SQL instance identifiers, database credentials, and weak JWT secrets in historical commits (`0ee47b5`), plus hardcoded database passwords in `docker-compose.yml` and Cloud Run origins in `WebSecurityConfig.java`.
   - Identified 3 high-priority application security blockers in the legacy authentication code and logged them as GitHub Issues `#4`, `#5`, and `#6`.
2. **GitHub Repository & Clean History Setup:**
   - Created public repository `https://github.com/ashiksp1994/SupportFlow-AI` with description, 11 topics, 5 phase milestones, custom labels, and 6 initial issues (`#1`–`#6`).
   - Renamed the legacy remote to `upstream` with `push` set to `DISABLED` and preserved the legacy branch locally as `legacy/physiotherapy-clinic`.
   - Initialized an orphan `main` branch (`git checkout --orphan main`) so zero historical commits or credentials can ever be pushed to `origin`.
3. **Phase 1 Backend Restructuring & Upgrade:**
   - Moved `user-service/` to `backend/` and migrated packages from `com.physiotherapy.userservice` / `com.physiotherapy.user_service` to `com.supportflow` and `com.supportflow.identity`.
   - Created `@ApplicationModule` package boundaries for all 11 modular monolith domains (`identity`, `organisations`, `customers`, `tickets`, `conversations`, `knowledge`, `ai`, `notifications`, `analytics`, `storage`, `audit`).
   - Upgraded Gradle Wrapper from `8.8` to `9.9.0` and added `org.gradle.toolchains.foojay-resolver-convention:1.0.0` so JDK 25 is automatically provisioned when the host only has a headless JRE installed.
   - Upgraded Spring Boot from `3.3.4` to `4.1.2` with Spring Modulith `2.1.2`, switched packaging from `war` to executable `jar`, and retired `ServletInitializer.java` and `user-service/compose.yaml`.
   - Removed legacy `javax.servlet:javax.servlet-api:4.0.1`, GCP-coupled `com.google.cloud.sql:postgres-socket-factory:1.20.1`, and unused `spring-cloud-dependencies`.
   - Restored `application.properties` with environment-variable placeholders, added Flyway migration `V1__init_user_schema.sql`, and fixed `application-test.properties` and `application-integration.properties`.
   - Fixed `UserService` dependency injection to use the `PasswordEncoder` interface bean rather than concrete `BCryptPasswordEncoder`.
   - Updated `backend/Dockerfile` to a multi-stage Java 25 build and updated root `docker-compose.yml` to use `pgvector/pgvector:pg17` and `./backend`.

### Files Changed
- Created: `.gitignore`, `.env.example`, `CHANGELOG.md`, `CONTRIBUTING.md`, `docs/PROJECT_LOG.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TECHNICAL_DECISIONS.md`
- Updated: `README.md`, `docker-compose.yml`, `.github/dependabot.yml`
- Moved & Modernised (`user-service/` -> `backend/`):
  - `backend/build.gradle`, `backend/settings.gradle`, `backend/Dockerfile`, `backend/gradle/wrapper/gradle-wrapper.properties`
  - `backend/src/main/java/com/supportflow/SupportFlowApplication.java`
  - `backend/src/main/java/com/supportflow/*/package-info.java` (11 modules)
  - `backend/src/main/java/com/supportflow/identity/**` (`AuthController`, `UserModel`, `UserRepository`, `JwtRequestFilter`, `JwtUtils`, `WebSecurityConfig`, `UserDetailsServiceImpl`, `UserService`)
  - `backend/src/main/resources/application.properties`
  - `backend/src/main/resources/db/migration/V1__init_user_schema.sql`
  - `backend/src/test/java/com/supportflow/SupportFlowApplicationTests.java`
  - `backend/src/test/java/com/supportflow/identity/ProfileTest.java`
  - `backend/src/test/java/com/supportflow/identity/UserModelTest.java`
  - `backend/src/test/resources/application-test.properties`
  - `backend/src/integrationTest/java/com/supportflow/identity/UserServiceIntegrationTest.java`
  - `backend/src/integrationTest/resources/application-integration.properties`
- Deleted: `user-service/compose.yaml`, `user-service/src/main/java/com/physiotherapy/userservice/ServletInitializer.java`

### Tests Executed & Results
- Command: `./gradlew clean build check --no-daemon`
- **Unit & Modulith Tests (`:test`):**
  - `SupportFlowApplicationTests.contextLoads()` — **PASSED**
  - `SupportFlowApplicationTests.verifiesModularStructure()` — **PASSED**
  - `ProfileTest.testActiveProfile()` — **PASSED**
  - `UserModelTest` (3 test methods) — **PASSED**
- **Integration Tests (`:integrationTest`):**
  - `UserServiceIntegrationTest.shouldPersistAndRetrieveUserWithEncodedPassword()` — **PASSED**

### Problems Discovered & Resolved
1. **Host Missing `javac` (`java-25-openjdk-headless` only):** Resolved without root privileges by adding `org.gradle.toolchains.foojay-resolver-convention:1.0.0` in `backend/settings.gradle`, enabling Gradle 9.9.0 to automatically provision a full JDK 25 toolchain.
2. **Testcontainers 2.x Artifact Coordinates in Spring Boot 4.1.2:** Spring Boot 4.1.2 manages Testcontainers 2.x, where coordinates changed from `org.testcontainers:postgresql` / `org.testcontainers:junit-jupiter` to `org.testcontainers:testcontainers-postgresql` and `org.testcontainers:testcontainers-junit-jupiter`. Updated `backend/build.gradle` accordingly.
3. **`PasswordEncoder` vs. `BCryptPasswordEncoder` Bean Type:** `WebSecurityConfig` exposed `PasswordEncoder` while `UserService` injected concrete `BCryptPasswordEncoder`. Updated `UserService` to inject the `PasswordEncoder` interface.

### Decisions Made
- Adopted a **Modular Monolith** architecture enforced by Spring Modulith rather than recreating empty microservices (recorded in `docs/TECHNICAL_DECISIONS.md` ADR-001).
- Standardized on **Java 25 LTS**, **Gradle 9.9.0**, and **Spring Boot 4.1.2** (ADR-002).
- Initialized a clean orphan Git history to prevent historical credential leakage (ADR-003).
- Adopted **Flyway** for deterministic database schema management with `spring.jpa.hibernate.ddl-auto=validate` (ADR-004).

### Remaining Work & Next Steps (Phase 2)
- Remediate high-priority security blockers tracked in GitHub Issues:
  - Issue `#4`: Remove `allowForRefreshToken` authentication bypass in `JwtRequestFilter` and implement secure refresh token handling.
  - Issue `#5`: Replace `UserModel` entity binding in `AuthController` with validated request/response `record` DTOs, prevent password hash serialization, and prevent mass role assignment.
  - Issue `#6`: Map user roles to `GrantedAuthority` in `UserDetailsServiceImpl` and enable `@EnableMethodSecurity`.

---

## Session 2 — 2026-11-10: Phases 2–5 Implementation (Security Hardening, Core Support Domain, RAG & AI Workflows, React Console & CI/CD)

- **Date:** 2026-11-10
- **Phases Completed:** Phase 2, Phase 3, Phase 4, and Phase 5
- **Objectives:**
  1. **Phase 2:** Remediate all three high-priority security blockers (Issues `#4`, `#5`, `#6`), implement refresh token rotation, RBAC method security, and the `audit` module.
  2. **Phase 3:** Implement `organisations`, `customers`, `tickets` (with SLA calculation & workload balancing), `conversations`, `storage` (S3/MinIO attachments), and `notifications` (Issues `#8`, `#9`, `#10`).
  3. **Phase 4:** Implement `knowledge` (articles, vector embeddings, hybrid RAG search) and `ai` (automated triage, RAG response suggestions with citations, conversation summarisation, and controlled AI tool calling with human approval gates) (Issues `#11`, `#12`, `#13`).
  4. **Phase 5:** Implement `analytics` dashboard API & enterprise demo seeder, build the `frontend/` React 19 + TypeScript + Vite + Tailwind CSS + TanStack Query + React Hook Form + Zod application with Vitest tests, and add GitHub Actions CI/CD (`.github/workflows/ci.yml`) (Issues `#14`, `#15`, `#16`).

### Tests Executed & Results
- **Backend (`./gradlew clean build check --no-daemon`):**
  - `SupportFlowApplicationTests` (Context & Spring Modulith verification across all 11 modules) — **PASSED**
  - `AuthSecurityIntegrationTest` (Registration DTO safety, login token issuance, 401 handling, refresh bypass regression test) — **PASSED**
  - `CoreSupportDomainTest` (Organisations, Customers, Tickets, SLA calculation, Escalation events, Conversations, Attachments) — **PASSED**
  - `AiWorkflowsIntegrationTest` (Knowledge Base indexing, RAG vector search, AI Triage, Cited RAG replies, Summarisation, Human-approved AI tool execution) — **PASSED**
  - `AnalyticsIntegrationTest` (Dashboard KPI calculation) — **PASSED**
  - `UserServiceIntegrationTest` (Integration suite) — **PASSED**
- **Frontend (`npm test && npm run build`):**
  - `src/test/App.test.tsx` (Vitest + React Testing Library) — **PASSED**
  - TypeScript compiler (`tsc -b`) & Vite production bundle (`vite build`) — **PASSED**
