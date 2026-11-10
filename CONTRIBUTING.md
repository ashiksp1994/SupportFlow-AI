# Contributing to SupportFlow AI

Thank you for contributing to **SupportFlow AI**. This document defines our engineering conventions, branching model, testing expectations, and documentation workflow.

---

## 1. Architecture & Code Conventions

- **Modular Monolith Boundaries:** All backend code lives under `backend/src/main/java/com/supportflow/<module>`. Modules must not reach into the internal repositories or entities of other modules directly; cross-module interaction must occur through public module services, DTOs, or Spring application events.
- **Java 25 & Spring Boot 4.x Standards:**
  - Prefer constructor injection over field `@Autowired` in all new or refactored classes.
  - Use Java `record` types for immutable API request/response DTOs and domain events.
  - Never expose JPA `@Entity` classes directly in `@RestController` request or response bodies.
  - All database schema modifications must be added as numbered Flyway SQL migrations in `backend/src/main/resources/db/migration/`.

---

## 2. Security Rules

- **Never commit secrets:** Do not commit `.env` files, API keys, database passwords, private keys, or cloud service account JSON files. Use `.env.example` with safe placeholder values only.
- **Validate all inputs:** Every API request DTO must use Jakarta Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, etc.) and `@Valid` on controller parameters.

---

## 3. Testing Requirements

Every change must pass the automated verification suite before committing:

```bash
cd backend
./gradlew clean check --no-daemon
```

- **Unit & Slice Tests (`src/test/java`):** Fast unit tests, MockMvc controller security tests, and Spring Modulith boundary verification (`ApplicationModules.of(SupportFlowApplication.class).verify()`).
- **Integration Tests (`src/integrationTest/java`):** End-to-end persistence and service integration tests.

---

## 4. Development & Documentation Workflow

For every milestone or issue completed:
1. Reference the relevant GitHub Issue in your work.
2. Run `./gradlew clean check` and verify all tests pass.
3. Update living documentation:
   - `docs/PROJECT_LOG.md` (session log, files changed, test results, decisions)
   - `docs/ROADMAP.md` (check off completed items)
   - `docs/ARCHITECTURE.md` and `docs/TECHNICAL_DECISIONS.md` (when architecture evolves)
   - `CHANGELOG.md` (for milestone changes)
4. Commit coherent, verified changes with a clear conventional commit message (e.g., `feat(identity): ...`, `fix(security): ...`, `chore(build): ...`).
