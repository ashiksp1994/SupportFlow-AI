# SupportFlow AI — Technical Decisions Log (ADRs)

This document records significant architectural and engineering decisions made during the development of SupportFlow AI.

---

## ADR-001: Adopt a Modular Monolith Architecture Instead of Microservices

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:**
  The legacy Physiotherapy Clinic repository referenced three backend microservices (`user-service`, `appointment-service`, `token-service`) plus `eureka-server`, `config-server`, and `api-gateway` in historical commits. However, our codebase audit revealed that only `user-service` contained any code (8 classes), while all other services were empty Spring Initializr skeletons deleted in commit `6025704`.
- **Decision:**
  Consolidate the backend into a single **Modular Monolith** (`supportflow-backend`) under base package `com.supportflow`, with 11 explicit top-level domain modules (`identity`, `organisations`, `customers`, `tickets`, `conversations`, `knowledge`, `ai`, `notifications`, `analytics`, `storage`, `audit`) verified by **Spring Modulith**.
- **Consequences:**
  - Eliminates distributed systems overhead (network latency, service discovery, distributed transactions, multi-JVM memory multiplication) while developing tightly coupled AI + ticketing workflows.
  - Enforces strict module encapsulation via `ApplicationModules.of(SupportFlowApplication.class).verify()` in automated tests.
  - Preserves the ability to extract individual modules into standalone services in the future if workload characteristics justify independent scaling.

---

## ADR-002: Standardize on Java 25 LTS, Spring Boot 4.1.2, and Gradle 9.9.0

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:**
  The legacy project used Gradle 8.8, Spring Boot 3.3.4, Java 21 in `build.gradle`, and `openjdk:17` in `Dockerfile`, packaged as a `.war` archive with legacy `javax.servlet:javax.servlet-api:4.0.1` and GCP-specific `com.google.cloud.sql:postgres-socket-factory`.
- **Decision:**
  Upgrade the build and runtime stack to **Java 25 LTS**, **Gradle 9.9.0** (with `org.gradle.toolchains.foojay-resolver-convention:1.0.0`), and **Spring Boot 4.1.2**, packaged as a self-contained executable JAR, and remove all `javax.servlet` and cloud-vendor-locked dependencies.
- **Consequences:**
  - Aligns build-time and container runtime Java versions on Java 25 LTS.
  - Foojay Toolchain Resolver ensures reproducible builds even on hosts where only a headless JRE is pre-installed.
  - Removes cloud vendor lock-in so the backend runs identically on local Podman/Docker and any cloud provider.

---

## ADR-003: Initialize a Clean Orphan Git History for the Public Repository

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:**
  Historical commits (`0ee47b5`, `c6ce415`) in the legacy repository contained hardcoded Google Cloud SQL instance names, database credentials (redacted), and JWT secrets.
- **Decision:**
  Never push or mirror legacy Git history to the new public repository (`https://github.com/ashiksp1994/SupportFlow-AI`). Preserve the old remote locally as `upstream` (with `push` set to `DISABLED`) and initialize a fresh orphan `main` branch (`git checkout --orphan main`) containing only sanitized, audited files.
- **Consequences:**
  - Zero historical credentials or personal cloud identifiers are exposed in the public repository's commit graph.
  - Local developers retain read-only access to the `legacy/physiotherapy-clinic` branch if historical reference is ever needed.

---

## ADR-004: Use PostgreSQL 17 with `pgvector` and Flyway Schema Migrations

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:**
  The legacy application used `spring.jpa.hibernate.ddl-auto=update` without database migrations and a plain `postgres:15` image. SupportFlow AI requires both relational integrity for tickets/SLAs and vector similarity search for RAG over knowledge base documents.
- **Decision:**
  Use `pgvector/pgvector:pg17` as the single primary database for both relational entities and vector embeddings, and manage all schema changes via **Flyway** (`classpath:db/migration`) with `spring.jpa.hibernate.ddl-auto=validate`.
- **Consequences:**
  - Version-controlled, repeatable database migrations across local development, CI, and production.
  - Avoids operational complexity of maintaining a separate dedicated vector database service.
