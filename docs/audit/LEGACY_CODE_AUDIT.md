# SupportFlow AI — Complete Legacy Code & Unused Artifact Audit (`LEGACY_CODE_AUDIT.md`)

**Audit Date:** 2026-11-10  
**Audited Commit:** `ed716bf` (`main`)  
**Scope:** Exhaustive search across Java source, React/TypeScript source, SQL migrations, tests, Dockerfiles, Gradle/npm dependencies, CI workflows, and documentation for remnants of the original Physiotherapy Clinic / `DevOpsProject` repository.

---

## 1. Search Methodology

Searched all tracked files (`git ls-files`) for:
- Domain terms: `physiotherapy`, `physiotherapist`, `appointment`, `clinic`, `patient`, `therapist`, `treatment`, `consultation`, `booking`, `dateOfBirth`, `date_of_birth`, `gender`.
- Legacy service/package names: `appointment-service`, `token-service`, `user-service`, `com.user`, `com.physiotherapy`, `com.physiotheraphy`, `eureka`, `config-server`.
- Legacy infrastructure: `cloud.sql`, `socket-factory`, `Jenkinsfile`, `cloudbuild.yaml`, `gke`, `war`.

---

## 2. Classified Inventory of Findings (`A` / `B` / `C` / `D`)

### Category A — Confirmed Legacy Code / Artifacts That Are No Longer Needed

| Item | Exact File Path(s) | Legacy Evidence & Line References | Usage Analysis | Removal / Cleanup Recommendation | Risk of Removal |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`LEG-001`** | `backend/src/main/java/com/supportflow/identity/model/UserModel.java` (`lines 28-29`)<br>`backend/src/main/java/com/supportflow/identity/dto/RegisterRequest.java` (`lines 22-23, 32-34`)<br>`backend/src/main/java/com/supportflow/identity/service/UserService.java` (`lines 89-90`) | Fields `private String dateOfBirth;` and `private String gender;` were patient demographic fields carried over verbatim from the Physiotherapy Clinic's `user-service` patient registration entity. | Never displayed in `UserResponse`, never used by any SupportFlow AI service or frontend component. (`V1__init_user_schema.sql` has nullable `date_of_birth` and `gender` columns). | Remove `dateOfBirth` and `gender` fields from `UserModel.java`, `RegisterRequest.java`, and `UserService.java`, and drop the unused columns in a clean Flyway migration `V6__drop_legacy_patient_user_columns.sql` (or leave nullable in DB while removing from Java model/DTO). | **Low** (only `RegisterRequest` callers in tests need 5 args instead of 7). |
| **`LEG-002`** | `backend/src/test/java/com/supportflow/identity/ProfileTest.java` (`lines 1-29`) | Legacy test copied from `user-service/src/test/java/com/physiotherapy/user_service/ProfileTest.java` that only runs `System.out.println("Active Profile: " + profile);`. | Redundant with `SupportFlowApplicationTests` and `AuthSecurityIntegrationTest`. | **Remove file** `backend/src/test/java/com/supportflow/identity/ProfileTest.java`. | **None** |
| **`LEG-003`** | `backend/src/test/java/com/supportflow/identity/UserModelTest.java` (`lines 1-59`) | Legacy unit test copied from `user-service` that sets `user.setRole("USER")` (`lines 18, 48, 54`), which was the legacy Physiotherapy Clinic role name rather than `CUSTOMER` / `AGENT` / `ADMIN`. | Tests basic getters/equals on `UserModel` using obsolete `"USER"` role string. | Either **remove** `UserModelTest.java` (since `AuthSecurityIntegrationTest` and `UserServiceIntegrationTest` test `UserModel` persistence and roles) or update it to test `UserRole.CUSTOMER` and `organisationId`. | **None** |
| **`LEG-004`** | `frontend/tsconfig.tsbuildinfo` | TypeScript incremental build cache artifact tracked in Git (`git ls-files`). | Generated automatically by `tsc -b`. Should not be tracked in version control. | Remove from Git index (`git rm --cached frontend/tsconfig.tsbuildinfo`). | **None** |
| **`LEG-005`** | `docs/PROJECT_LOG.md` & `docs/ROADMAP.md` | Early Phase 1 markdown files superseded by `docs/DEVELOPMENT_LOG.md` and `docs/PROJECT_ROADMAP.md` (and root `ROADMAP.md`). | Duplicate documentation files in `docs/`. | Merge any unique historical notes into `docs/DEVELOPMENT_LOG.md` and `docs/PROJECT_ROADMAP.md`, and remove duplicate `docs/PROJECT_LOG.md` and `docs/ROADMAP.md`. | **None** |

---

### Category B — Preserved & Modernised Reusable Foundation Code

| Component | Exact File Path(s) | Original Source & How It Was Modernised |
| :--- | :--- | :--- |
| **Identity Authentication Core** | `backend/src/main/java/com/supportflow/identity/controller/AuthController.java`<br>`backend/src/main/java/com/supportflow/identity/service/UserService.java`<br>`backend/src/main/java/com/supportflow/identity/service/UserDetailsServiceImpl.java`<br>`backend/src/main/java/com/supportflow/identity/security/JwtUtils.java`<br>`backend/src/main/java/com/supportflow/identity/security/JwtRequestFilter.java`<br>`backend/src/main/java/com/supportflow/identity/security/WebSecurityConfig.java` | Originated from `user-service` (`com.user.*`), relocated to `com.supportflow.identity.*`, upgraded to Java 25 / Spring Boot 4.1.2 / JJWT 0.12.6, and hardened with `UserRole` RBAC, SHA-256 refresh token rotation + replay detection, `/api/auth/logout`, rate limiting, and `AccessControlService` multi-tenancy. |
| **PostgreSQL Testcontainer Integration Test** | `backend/src/integrationTest/java/com/supportflow/identity/UserServiceIntegrationTest.java` | Modernised from the legacy integration test to use Testcontainers 2.x with `pgvector/pgvector:pg17` and Flyway `V1`–`V5` validation. |

---

### Category C — Intentional SupportFlow AI Functionality (100% Active)

- All 11 `@ApplicationModule` packages under `backend/src/main/java/com/supportflow/{ai,analytics,audit,conversations,customers,identity,knowledge,notifications,organisations,storage,tickets}`.
- All Flyway migrations `V1`–`V5` under `backend/src/main/resources/db/migration/`.
- All React 19 + TypeScript files under `frontend/src/{App.tsx,main.tsx,index.css,lib/api.ts,components/*,test/*}`.
- Container & deployment manifests: `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/vercel.json`, `render.yaml`, `docker-compose.yml`, `.github/workflows/ci.yml`.

---

### Category D — Uncertain Items Requiring Investigation

- **None.** Every single file in `git ls-files` (97 tracked files total) has been inspected and accounted for above.
