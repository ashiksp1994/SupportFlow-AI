# SupportFlow AI — Complete Git Repository Audit (`GIT_AUDIT.md`)

**Audit Date:** 2026-11-10  
**Audited HEAD:** `ed716bf` (`main`)  
**Repository:** `https://github.com/ashiksp1994/SupportFlow-AI`

---

## 1. Current Branch & Working Tree Status (`git status`)

- **Current Branch:** `main`
- **Upstream Tracking Branch:** `origin/main` (`ed716bf`)
- **Uncommitted Changes (`git diff` / `git diff --cached`):** None (working tree clean prior to audit document generation).
- **Deleted Tracked Files (`git ls-files --deleted`):** None.

---

## 2. Repository Remotes (`git remote -v`)

| Remote Name | Fetch URL | Push URL | Purpose & Safety Posture |
| :--- | :--- | :--- | :--- |
| **`origin`** | `https://github.com/ashiksp1994/SupportFlow-AI.git` | `https://github.com/ashiksp1994/SupportFlow-AI.git` | Active public SupportFlow AI repository. |
| **`upstream`** | `https://github.com/ashiksp1994/DevOpsProject.git` | `DISABLED` | Legacy Physiotherapy Clinic reference repository (push explicitly disabled to prevent accidental pushes). |

---

## 3. Branch & Tag Inventory (`git branch -avv` & `git tag -n`)

### Local Branches
| Branch | Tip Commit | Relationship to `main` | Classification |
| :--- | :--- | :--- | :--- |
| **`main`** | `ed716bf` | Active `HEAD` (`6` commits, orphan root `2c9c857`) | Active SupportFlow AI branch |
| **`legacy/physiotherapy-clinic`** | `503ba05` | Unmerged (`git merge-base main legacy/physiotherapy-clinic` returns no common ancestor) | Local-only historical archive of the legacy Physiotherapy Clinic repository |

### Remote Tracking Branches (`origin`)
- `remotes/origin/main` (`ed716bf`)
- `6` open Dependabot PR branches on `origin` (`refs/pull/7`, `#17`, `#18`, `#19`, `#20`, `#21` — investigated in Section 6).

### Remote Tracking Branches (`upstream` — Legacy `DevOpsProject`)
- `remotes/upstream/main` (`503ba05`)
- `remotes/upstream/master` (`c6ce415`)
- `9` legacy Dependabot branches under `remotes/upstream/dependabot/gradle/{appointment-service,token-service,user-service}/*`.

### Tags (`git tag -n`)
- None (`0` tags).

---

## 4. Commit History & Lineage Analysis (`git log --graph --oneline --decorate --all`)

### `main` Branch Commit History (Clean Independent Orphan Lineage)
`git merge-base main legacy/physiotherapy-clinic` confirms **zero shared commit ancestry** between `main` and `legacy/physiotherapy-clinic`. The `main` branch consists of 6 purpose-built SupportFlow AI commits:
1. `2c9c857` — `feat(phase-1): initialise SupportFlow AI modular monolith on Java 25 and Spring Boot 4.1.2`
2. `b1df571` — `feat(phase-2): remediate auth security blockers, add RBAC, refresh rotation, and audit module`
3. `00a17eb` — `feat(phase-3): implement organisations, customers, tickets, SLA tracking, conversations, and S3 storage`
4. `8b4b363` — `feat(phase-4): implement knowledge base RAG search, AI triage, citations, summarisation, and tool approval gates`
5. `73cf318` — `feat(phase-5): add analytics API, React 19 + TypeScript console, CI workflow, and full stack`
6. `ed716bf` — `feat(production): harden multi-tenancy, RBAC, grounded RAG, security & cloud deployment`

### Legacy Accessibility Assessment
- **On Public GitHub (`origin`):** Only `main` (and Dependabot branches forked from `2c9c857`/`73cf318`) exist on `origin`. The `legacy/physiotherapy-clinic` commit graph (`503ba05` and its parents) was **never pushed** to `origin`.
- **In Local `.git/objects`:** Because this local workspace was cloned from `DevOpsProject`, the local `.git` directory still holds `legacy/physiotherapy-clinic` (`503ba05`) and `remotes/upstream/*` tracking refs as intended by the initial preservation requirement.

---

## 5. Tracked File Hygiene Issues Identified on `main`

1. **`frontend/tsconfig.tsbuildinfo` (Tracked Build Cache File):**
   - Present in `git ls-files` (`frontend/tsconfig.tsbuildinfo`), committed in `73cf318` before `tsconfig.tsbuildinfo` was added to `.gitignore`.
   - **Recommendation:** Untrack from Git index via `git rm --cached frontend/tsconfig.tsbuildinfo` (keeping `.gitignore` rule).
2. **Duplicate Documentation Files Created Across Phases:**
   - `docs/PROJECT_LOG.md` (created in Phase 1) vs `docs/DEVELOPMENT_LOG.md` (created in Phase 5/10).
   - `docs/ROADMAP.md` and root `ROADMAP.md` vs `docs/PROJECT_ROADMAP.md`.
   - Root `SECURITY.md` vs `docs/SECURITY.md`.
   - **Recommendation:** Consolidate content so there are no conflicting duplicate docs.
