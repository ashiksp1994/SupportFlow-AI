# SupportFlow AI — SRE & Operations Runbook (`docs/OPERATIONS.md`)

## 1. Health Checks & Readiness Probes

- **Liveness / Readiness Endpoint:** `GET /actuator/health`
  - Returns HTTP `200 OK` (`{"status":"UP"}`) when the Spring Boot context and PostgreSQL DataSource are healthy.
- **Build & Info Endpoint:** `GET /actuator/info`

## 2. Request Correlation & Structured Audit Logging

- **Correlation IDs (`CorrelationIdFilter`):**
  - Every incoming HTTP request reads `X-Correlation-Id` (or generates a UUIDv4 if absent), binds `correlationId` to SLF4J `MDC`, and returns `X-Correlation-Id` in the response headers.
- **Security & AI Governance Audit Trail (`audit_logs`):**
  - Query via `GET /api/audit-logs` (requires `ROLE_ADMIN` or `ROLE_AGENT`).
  - Recorded events include: `USER_REGISTERED`, `AUTH_LOGIN_SUCCESS`, `AUTH_LOGIN_FAILED`, `AUTH_RATE_LIMITED`, `AUTH_TOKEN_REFRESHED`, `SECURITY_TOKEN_REUSE_DETECTED`, `AUTH_LOGOUT`, `TICKET_CREATED`, `TICKET_ASSIGNED`, `TICKET_UPDATED`, `TICKET_ESCALATED`, `CONVERSATION_MESSAGE_ADDED`, `KB_ARTICLE_PUBLISHED`, `AI_TICKET_TRIAGED`, `AI_PROMPT_INJECTION_DETECTED`, `AI_RAG_REPLY_GENERATED`, `AI_RAG_INSUFFICIENT_EVIDENCE`, `AI_TOOL_APPROVAL_REQUESTED`, `AI_TOOL_PROPOSAL_BLOCKED_INJECTION`, `AI_TOOL_APPROVAL_EXECUTED`, and `AI_TOOL_APPROVAL_REJECTED`.

## 3. Incident Response Procedures

1. **Suspected Refresh Token Compromise (`SECURITY_TOKEN_REUSE_DETECTED`):**
   - Automatic mitigation: `UserService.refreshToken` immediately revokes all active refresh tokens for the affected user.
   - Operator action: Inspect `GET /api/audit-logs` for the actor's recent actions and rotate the user's password if credential compromise is suspected.
2. **Prompt Injection Alert (`AI_PROMPT_INJECTION_DETECTED` / `AI_TOOL_PROPOSAL_BLOCKED_INJECTION`):**
   - Automatic mitigation: `PromptInjectionGuard` redacts injection sequences and blocks AI tool proposals.
   - Operator action: Review the flagged ticket key in the Ticket Workspace and handle manually without AI tool automation.
3. **Database Migration Failure on Startup:**
   - Flyway migrations (`V1`–`V5`) execute inside transactional DDL blocks on PostgreSQL. Verify `flyway_schema_history` and restore from the latest Neon point-in-time branch if needed.
