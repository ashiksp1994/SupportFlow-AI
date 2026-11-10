# SupportFlow AI — Cloud & Container Deployment Guide (`docs/DEPLOYMENT.md`)

## 1. Deployment Topologies

SupportFlow AI supports two primary deployment profiles:

1. **Local Podman / Docker Compose Stack (`docker-compose.yml`):**
   - Core active services: `postgres` (`pgvector/pgvector:pg17`), `backend` (Java 25 JRE Alpine), and `frontend` (Nginx 1.27 Alpine with SPA routing and `/api/` reverse proxy).
   - Start core stack:
     ```bash
     podman compose up --build -d
     ```
   - Optional infrastructure (`redis`, `minio`) is isolated behind the `full-infra` profile:
     ```bash
     podman compose --profile full-infra up -d
     ```

2. **€0/Month Portfolio Demonstration Cloud Stack (Vercel + Render + Neon):**
   - **Database (Neon Serverless PostgreSQL 17):**
     - Create a free Neon project (`PostgreSQL 17`).
     - Copy the pooled/direct JDBC connection string with SSL enabled:
       `jdbc:postgresql://<endpoint>.neon.tech/neondb?sslmode=require`
   - **Backend (Render Free Web Service via `render.yaml`):**
     - Connect the GitHub repository `https://github.com/ashiksp1994/SupportFlow-AI` to Render Blueprint (`render.yaml`).
     - Set environment variables:
       - `SPRING_PROFILES_ACTIVE=prod`
       - `SPRING_DATASOURCE_URL=jdbc:postgresql://<endpoint>.neon.tech/neondb?sslmode=require`
       - `SPRING_DATASOURCE_USERNAME=<neon_user>`
       - `SPRING_DATASOURCE_PASSWORD=<neon_password>`
       - `JWT_SECRET=<base64-encoded-256-bit-secret>`
       - `CORS_ALLOWED_ORIGINS=https://<your-project>.vercel.app`
       - `APP_DEMO_SEED_ENABLED=true` (for portfolio demo) or `false` (for clean production)
   - **Frontend (Vercel Hobby via `frontend/vercel.json`):**
     - Import `frontend/` as the root directory in Vercel.
     - Set `VITE_API_BASE_URL=https://<your-render-service>.onrender.com`.

---

## 2. Honest Free-Tier Operational Limitations

The €0/month cloud deployment is designed for **public portfolio demonstration** and has the following platform constraints compared to paid commercial HA infrastructure:
- **Render Free Web Service Cold Starts:** Spins down after 15 minutes of inactivity; the first request after idle takes ~30–50 seconds while the JVM starts.
- **Neon Free Compute Auto-Suspend & Connection Limits:** Neon free compute suspends after 5 minutes of inactivity (~500ms–2s wake latency). `application-prod.properties` restricts HikariCP to `maximum-pool-size=5` and `minimum-idle=1`.
- **In-Memory Rate Limiting:** Login brute-force buckets live in JVM memory on the single Render instance. Multi-replica horizontal scaling requires shifting rate-limit buckets to Upstash Redis.
