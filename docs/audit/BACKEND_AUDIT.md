# SupportFlow AI — Backend Architecture & Concurrency Audit (`BACKEND_AUDIT.md`)

**Audit Phase:** Phase 2  
**Date:** 2026-11-10  
**Stack Verified:** Java 25 LTS, Spring Boot `4.1.2`, Spring Modulith `2.1.2`, Gradle `9.9.0`.

---

## 1. Module Boundary Verification (Spring Modulith)

SupportFlow AI is structured as a single deployable modular monolith with **11 `@ApplicationModule` packages** verified by `SupportFlowApplicationTests.verifiesModularStructure()`:

1. `com.supportflow.audit` — Immutable event recording (`AuditService`, `AuditController`, `AuditLog`).
2. `com.supportflow.identity` — Authentication, JWT, refresh tokens, `AccessControlService`, `CorrelationIdFilter`.
3. `com.supportflow.organisations` — Tenant organisations and SLA policy tiers (`OrganisationService`, `OrganisationController`).
4. `com.supportflow.customers` — Customer profiles (`CustomerService`, `CustomerController`).
5. `com.supportflow.tickets` — Ticket lifecycle, SLA calculation, assignment, escalation, workload (`TicketService`, `TicketController`).
6. `com.supportflow.conversations` — Customer replies and internal agent notes (`ConversationService`, `ConversationController`).
7. `com.supportflow.storage` — S3/MinIO attachment metadata & signed download URLs (`StorageService`, `StorageController`, `TicketAttachment`).
8. `com.supportflow.knowledge` — Knowledge base articles, chunking (`KnowledgeChunk`), and hybrid RAG retrieval (`KnowledgeService`, `KnowledgeController`).
9. `com.supportflow.ai` — Triage, grounded RAG reply suggestions, summarisation, `PromptInjectionGuard`, `AllowedAiTool`, `LlmProviderClient`, and human approval workflow (`AiOrchestrationService`, `AiController`).
10. `com.supportflow.notifications` — Event-driven listeners (`SupportNotificationListener`).
11. `com.supportflow.analytics` — Tenant-aware operational KPIs (`AnalyticsService`, `AnalyticsController`).

---

## 2. Remediated Backend Findings (`ARCH-001`)

1. **Extracted Nested Controllers & Services into Top-Level Classes:**
   - Extracted `OrganisationController`, `CustomerController`, `TicketController`, `ConversationController`, `StorageService`, `StorageController`, `KnowledgeController`, `AiController`, and `AnalyticsController` out of `@Service` and `@Entity` classes into dedicated top-level files.
2. **Optimistic Locking (`@Version`):**
   - Added `@Version private long version` to `Ticket` and `AiApprovalRequest` (backed by `V5` migration) to prevent lost updates and race-condition double execution of approved AI tools.
3. **Jackson 3 / Spring Boot 4 Null-Safe Request Records:**
   - Boxed optional boolean fields (`Boolean internalNote`, `Boolean aiGenerated`, `Boolean vip`) in request DTO records so omitted fields do not trigger Jackson 3 `FAIL_ON_NULL_FOR_PRIMITIVES` `400 Bad Request` errors.
4. **Pagination & Attachment Validation:**
   - Added `page` and `size` bounds on `GET /api/tickets`.
   - Added 25 MiB size limit, MIME allowlist validation, and filename sanitization in `StorageService.registerAttachment`.
