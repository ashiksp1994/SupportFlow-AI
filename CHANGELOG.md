# Changelog

All notable changes to **SupportFlow AI** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-alpha.1] — 2026-11-10

### Added
- Initialized clean public repository history for **SupportFlow AI** (`https://github.com/ashiksp1994/SupportFlow-AI`).
- Established `backend/` modular monolith structure under package `com.supportflow` with `@ApplicationModule` descriptors for `identity`, `organisations`, `customers`, `tickets`, `conversations`, `knowledge`, `ai`, `notifications`, `analytics`, `storage`, and `audit`.
- Added Spring Modulith structural verification test (`SupportFlowApplicationTests.verifiesModularStructure`).
- Added Flyway database migration baseline (`V1__init_user_schema.sql`) and `UserServiceIntegrationTest`.
- Added `.env.example` template and root `.gitignore`.
- Created project documentation suite (`README.md`, `docs/PROJECT_LOG.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/TECHNICAL_DECISIONS.md`, `CONTRIBUTING.md`, `CHANGELOG.md`).

### Changed
- Upgraded build system from Gradle 8.8 to **Gradle 9.9.0** with Foojay Java Toolchain auto-provisioning.
- Upgraded Java toolchain from Java 21 (and Dockerfile Java 17) to **Java 25 LTS**.
- Upgraded Spring Boot from 3.3.4 to **Spring Boot 4.1.2** and switched packaging from `war` to executable `jar`.
- Restored environment-variable-driven `application.properties` and fixed test profile configurations (`application-test.properties`, `application-integration.properties`).
- Updated `backend/Dockerfile` to a multi-stage, non-root `eclipse-temurin:25` build and updated `docker-compose.yml` to use `pgvector/pgvector:pg17`.

### Removed
- Removed obsolete `ServletInitializer.java` and empty `user-service/compose.yaml`.
- Removed legacy `javax.servlet:javax.servlet-api:4.0.1`, GCP-specific `com.google.cloud.sql:postgres-socket-factory:1.20.1`, and unused `spring-cloud-dependencies`.
- Removed hardcoded Cloud Run CORS origin from `WebSecurityConfig.java`.

### Security
- Scrubbed all historical credentials by initializing an orphan Git branch before publishing.
- Documented pre-existing legacy authentication vulnerabilities (`JwtRequestFilter` refresh bypass, `AuthController` entity exposure, `UserDetailsServiceImpl` empty authorities) as high-priority Phase 2 blockers (GitHub Issues `#4`, `#5`, `#6`).
