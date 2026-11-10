# SupportFlow AI — Application Security & Multi-Tenancy Audit (`SECURITY_AUDIT.md`)

**Audit Phase:** Phase 1  
**Date:** 2026-11-10  
**Scope:** OWASP Top 10, OWASP API Security Top 10, OWASP Top 10 for LLM Applications, Authentication, JWT & Refresh Token Lifecycle, RBAC, and Multi-Organisation Data Isolation.

---

## 1. Threat Model & Trust Boundaries

1. **Public Internet -> REST API (`/api/**`):**
   - Untrusted callers. Only `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/actuator/health`, and `/actuator/info` are unauthenticated (`WebSecurityConfig.java`).
2. **Authenticated Tenant Boundary (`CUSTOMER` vs `AGENT` vs `ADMIN`):**
   - Horizontal isolation enforced by `AccessControlService.verifyTenantAccess(organisationId, ownerIdentifier)` (`AccessControlService.java`).
   - Vertical role isolation enforced by `@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")` and `AccessControlService.requireStaff()`.
3. **Untrusted Text -> AI / RAG Pipeline:**
   - Screened by `PromptInjectionGuard.inspect(...)` before triage, RAG search, or tool proposal (`PromptInjectionGuard.java`).
4. **AI Agent -> Domain State Mutations:**
   - Gated by `AllowedAiTool` enum allowlist, JSON parameter validation, `@Version` optimistic locking, and mandatory human supervisor decision (`AiOrchestrationService.decideApproval`).

---

## 2. Remediated Security Findings

| ID | Severity | Vulnerability | Remediation Implemented | Verification Test |
| :--- | :---: | :--- | :--- | :--- |
| **`SEC-001`** | **P0** | Missing multi-tenancy (`UserModel` lacked `organisationId`; services returned cross-tenant tickets/messages/customers). | Added `organisation_id` to `user_details` & `knowledge_articles` (`V5` migration). Implemented `AccessControlService` enforcing tenant checks in `TicketService`, `ConversationService`, `CustomerService`, `OrganisationService`, `KnowledgeService`, `AiOrchestrationService`, and `AnalyticsService`. | `ProductionReadinessSecurityAndAiTest.shouldEnforceMultiTenantIsolationRbacAndHideInternalNotesFromCustomers` |
| **`SEC-002`** | **P0** | Missing `@PreAuthorize` on staff endpoints and leakage of `internalNote = true` messages to customers. | Added `@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")` across all staff endpoints; filtered out `internalNote == true` messages for `CUSTOMER` callers in `ConversationService.getMessagesForTicket`; prevented customer sender/note spoofing in `ConversationService.addMessage`. | `ProductionReadinessSecurityAndAiTest.shouldEnforceMultiTenantIsolationRbacAndHideInternalNotesFromCustomers` |
| **`SEC-003`** | **P1** | Default fallback `JWT_SECRET` and enabled demo seeding in production. | `JwtUtils` rejects startup in `prod` profile if `JWT_SECRET` matches the dev fallback key; `application-prod.properties` requires explicit `JWT_SECRET` and defaults `app.demo.seed-enabled=false`. | `AuthSecurityIntegrationTest` & `JwtUtils.java` |
| **`SEC-004`** | **P1** | Plaintext refresh tokens, no token reuse detection, no `/api/auth/logout`, no login rate limit. | `UserService` hashes refresh tokens with SHA-256 (`UserService.hashToken`), revokes all active user tokens when a revoked token is replayed (`SECURITY_TOKEN_REUSE_DETECTED`), exposes `POST /api/auth/logout`, and enforces per-username login rate limiting (`429 Too Many Requests`). | `ProductionReadinessSecurityAndAiTest.shouldHashRefreshTokensDetectTokenReuseRevokeOnLogoutAndEnforceLoginRateLimit` |

---

## 3. OWASP API & LLM Security Verification Checklist

- **API1:2023 Broken Object Level Authorization:** Remediated via `AccessControlService.verifyTenantAccess` on ticket, customer, organisation, message, attachment, and KB lookups.
- **API2:2023 Broken Authentication:** Remediated via BCrypt password hashing, 256-bit HMAC-SHA256 JWTs with issuer verification, SHA-256 hashed refresh tokens, token rotation + replay family revocation, and login rate limiting.
- **API3:2023 Broken Object Property Level Authorization:** Public registration (`UserService.registerCustomer`) hardcodes `UserRole.CUSTOMER`; customer message posting forces `senderType = "CUSTOMER"` and `internalNote = false`; `UserModel.password` is `@JsonIgnore`.
- **API5:2023 Broken Function Level Authorization:** Remediated via `@EnableMethodSecurity` + `@PreAuthorize` + `AccessControlService.requireStaff()`.
- **LLM01:Prompt Injection & LLM07:Insecure Plugin/Tool Design:** Remediated via `PromptInjectionGuard`, `AllowedAiTool` allowlist, and mandatory human approval before tool execution.
