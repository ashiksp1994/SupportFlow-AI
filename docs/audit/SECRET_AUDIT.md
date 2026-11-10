# SupportFlow AI — Secret & Historical Credential Audit (`SECRET_AUDIT.md`)

**Audit Date:** 2026-11-10  
**Audited Repository:** `/home/ashiksprasanna/develop/SupportFlow-AI`  
**Scope:** Tracked files on `main`, `.env.example`, Docker/Compose manifests, GitHub Actions, test fixtures, and historical commits on `main` vs. local `legacy/physiotherapy-clinic`.  
*(Note: No raw secret values are printed in this report.)*

---

## 1. Current Tracked Files on `main` (`ed716bf`)

| File Inspected | Finding | Risk Level | Action Required |
| :--- | :--- | :---: | :--- |
| `backend/src/main/resources/application.properties` | Uses `${JWT_SECRET:U3VwcG9ydEZsb3dBSURldk9ubHlGYWxsYmFja1NlY3JldEtleUZvckxvY2FsVGVzdGluZzIwMjY=}` (explicitly labeled dev-only Base64 string `SupportFlowAIDevOnlyFallbackSecretKeyForLocalTesting2026`) and local dev DB defaults (`supportflow_dev_pass`). | **Low (Dev Only)** | `JwtUtils` explicitly blocks startup if the `prod` profile is active and `JWT_SECRET` equals this dev fallback string. Furthermore, in Stage 6 we will move demo user seeding (`app.demo.seed-enabled=true`) exclusively into `application-demo.properties` so `application.properties` defaults `app.demo.seed-enabled` to `false`. |
| `backend/src/main/resources/application-prod.properties` | Uses strict environment variable placeholders (`${SPRING_DATASOURCE_URL}`, `${SPRING_DATASOURCE_USERNAME}`, `${SPRING_DATASOURCE_PASSWORD}`, `${JWT_SECRET}`, `${CORS_ALLOWED_ORIGINS}`) with **no fallback secret** and `app.demo.seed-enabled=${APP_DEMO_SEED_ENABLED:false}`. | **None (Clean)** | None. |
| `backend/src/test/resources/application-test.properties` | Uses a test-only Base64 string (`TestOnlyJwtSecretKeyForSupportFlowAIUnitTests256BitsLong`) against in-memory H2 (`sa` / empty password). | **None (Test Only)** | None. |
| `.env.example` & `render.yaml` | Contains placeholder templates (`replace_with_256_bit_base64_secret`, `sync: false`, `generateValue: true`). | **None (Clean)** | None. |

---

## 2. Commit History Audit on `main` (`2c9c857`..`ed716bf`)

- All 6 commits on `main` (`2c9c857`, `b1df571`, `00a17eb`, `8b4b363`, `73cf318`, `ed716bf`) were scanned.
- **Finding:** Commit `2c9c857` (the orphan root commit of `main`) never included the legacy `Jenkinsfile`, `cloudbuild.yaml`, or the legacy `user-service/src/main/resources/application.properties`.
- **Verdict for Public `origin/main` (`https://github.com/ashiksp1994/SupportFlow-AI`):** **Zero historical production/cloud secrets exist in the commit graph of `origin/main`.**

---

## 3. Historical Credentials in Local `legacy/physiotherapy-clinic` (`upstream/main`)

Although **not present on `origin/main`**, the preserved local branch `legacy/physiotherapy-clinic` (`503ba05`) and `remotes/upstream/*` (from `https://github.com/ashiksp1994/DevOpsProject.git`) contain historical commits from 2024 with legacy configuration material:

| Legacy Commit(s) | Legacy File Path | Credential Type | Validity & Remediation Recommendation |
| :--- | :--- | :--- | :--- |
| `0ee47b5`, `c6ce415`, `503ba05` | `user-service/src/main/resources/application.properties` | Hardcoded Google Cloud SQL instance connection name, legacy PostgreSQL password, and legacy static JWT signing key (`JwtUtils.java`). | **Action:** Ensure the 2024 GCP Cloud SQL instance and database user from `DevOpsProject` are decommissioned/rotated in Google Cloud Console. Do **not** push `legacy/physiotherapy-clinic` to `origin`. |
| `8648d7e`, `5062242` | `Jenkinsfile`, `cloudbuild.yaml` | Legacy GCP project ID and GKE cluster/region identifiers. | Informational project metadata; keep `upstream` push disabled (`DISABLED`). |
