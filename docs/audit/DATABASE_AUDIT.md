# SupportFlow AI — Database Schema, Flyway & Indexing Audit (`DATABASE_AUDIT.md`)

**Audit Phase:** Phase 5  
**Date:** 2026-11-10  
**Database Engine:** PostgreSQL 17 (`pgvector/pgvector:pg17` in containers/CI, Neon Serverless PostgreSQL in cloud, H2 in PostgreSQL compatibility mode for fast unit slice tests).

---

## 1. Flyway Migration Inventory (`V1` – `V5`)

| Version | File | Purpose |
| :--- | :--- | :--- |
| **`V1`** | `V1__init_user_schema.sql` | Initial `user_details` table and unique constraints. |
| **`V2`** | `V2__security_and_audit_schema.sql` | `refresh_tokens` and `audit_logs` tables with user FK and token index. |
| **`V3`** | `V3__core_support_domain_schema.sql` | `organisations`, `customers`, `tickets`, `conversation_messages`, and `ticket_attachments` tables. |
| **`V4`** | `V4__knowledge_and_ai_workflows_schema.sql` | `knowledge_articles` and `ai_approval_requests` tables. |
| **`V5`** | `V5__multi_tenancy_rag_chunks_and_locking.sql` | Adds `organisation_id` to `user_details` and `knowledge_articles`, adds `version` optimistic locking columns to `tickets` and `ai_approval_requests`, creates `knowledge_chunks`, and adds composite/FK indexes. |

---

## 2. Remediated Database Findings (`DB-001`)

1. **Fixed Entity-Column Mapping Mismatch in `TicketAttachment`:**
   - Mapped `@Column(name = "file_size_bytes")` and `@Column(name = "storage_bucket")` to match `V3__core_support_domain_schema.sql` under `spring.jpa.hibernate.ddl-auto=validate`.
2. **Added Multi-Tenancy & Foreign-Key Indexes (`V5`):**
   - `idx_user_details_org_id` on `user_details(organisation_id)`
   - `idx_customers_org_id` on `customers(organisation_id)`
   - `idx_tickets_org_status_priority` on `tickets(organisation_id, status, priority)`
   - `idx_tickets_customer_id` on `tickets(customer_id)`
   - `idx_tickets_assigned_agent` on `tickets(assigned_agent_username)`
   - `idx_kb_articles_org_category` on `knowledge_articles(organisation_id, category)`
   - `idx_kb_chunks_article_id` on `knowledge_chunks(article_id)`
   - `idx_audit_logs_created_at` on `audit_logs(created_at)`
3. **Neon Serverless PostgreSQL Connection Pooling (`application-prod.properties`):**
   - Configured HikariCP with `maximum-pool-size=5`, `minimum-idle=1`, `idle-timeout=30000`, and `max-lifetime=600000` to prevent connection exhaustion on Neon free-tier compute endpoints.
