# SupportFlow AI — Security Architecture & Policy (`docs/SECURITY.md`)

## 1. Authentication & Token Security

- **Password Hashing:** BCrypt (`BCryptPasswordEncoder`) via Spring Security. Plaintext passwords are never logged or serialized (`@JsonIgnore` on `UserModel.password`).
- **Access Tokens:** Short-lived HMAC-SHA256 JWTs (`15` minutes default, `iss = "supportflow-ai"`). Expired JWTs are rejected immediately in `JwtRequestFilter`.
- **Refresh Tokens:**
  - Stored hashed with **SHA-256** (`UserService.hashToken`) in `refresh_tokens.token`.
  - Single-use rotation on `POST /api/auth/refresh`.
  - **Token Reuse Detection:** If a previously revoked refresh token is replayed, all active refresh tokens for that user are immediately revoked and a `SECURITY_TOKEN_REUSE_DETECTED` audit event is recorded.
  - **Explicit Logout:** `POST /api/auth/logout` revokes active refresh tokens.
- **Brute-Force Rate Limiting:** Per-username sliding window rate limit on `POST /api/auth/login` (`10` failed attempts/minute in dev/test, `8`/minute in `prod`), returning `429 Too Many Requests`.

## 2. Role-Based Access Control (RBAC) & Multi-Tenancy

- **Roles:** `ROLE_ADMIN`, `ROLE_AGENT`, `ROLE_CUSTOMER`.
- **Tenant Isolation (`AccessControlService`):**
  - `CUSTOMER` accounts and tenant-bound accounts (`organisation_id != null`) are strictly restricted to their own organisation's tickets, conversations, customers, attachments, and private KB articles.
  - Internal agent notes (`internalNote == true`) are stripped from `GET /api/tickets/{id}/messages` responses when queried by a `CUSTOMER`.

## 3. AI & Tool Governance Security

- **Prompt Injection Defense:** `PromptInjectionGuard` inspects and sanitizes untrusted ticket/message text and blocks tool proposals when injection signatures are detected.
- **Controlled Tool Allowlist:** Only tools in `AllowedAiTool` (`ISSUE_SLA_SERVICE_CREDIT`, `ESCALATE_TO_TIER3_ENGINEERING`, `UPDATE_SLA_TIER`, `CLOSE_DUPLICATE`) can be proposed, and none can execute without human `ADMIN` or `AGENT` approval.
