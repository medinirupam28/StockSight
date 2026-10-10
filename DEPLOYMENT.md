# StockSight Deployment and Validation

Cloud resources are not provisioned by this repository. The steps below prepare a Vercel frontend, Render Docker backend, and managed AWS RDS MySQL database; run the checks in your own accounts before treating a deployment as complete.

## Local Docker Development
Prerequisites: Docker Desktop and a Vercel-free local workspace. From the repository root:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Populate `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `JWT_SECRET` (at least 32 random bytes), and `FRONTEND_ORIGIN` in the ignored `.env` file. `ADMIN_USERNAME`/`ADMIN_PASSWORD` are optional; if omitted, create a supplier account and promote a trusted account out-of-band. Do not commit `.env` or paste its contents into logs/issues.

Start the existing frontend in a second PowerShell terminal:

```powershell
Set-Location .\frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The backend is at `http://localhost:8080`; health is `http://localhost:8080/actuator/health`; Swagger is `http://localhost:8080/swagger-ui/index.html`. MySQL data persists in Compose volume `stocksight_mysql`. Do not use `docker compose down -v` for routine shutdown.

## Schema and Persistence
- Flyway migration `V1__initial_schema.sql` creates the current schema for a fresh database.
- `baseline-on-migrate` baselines a non-empty schema at version 1. The current MySQL schema was inspected and boot-validated without changing product, movement, or user rows.
- Hibernate uses `ddl-auto=validate`; future schema changes belong in new Flyway migrations. `flyway.clean` is disabled.
- Take a verified backup before applying future migrations to production.

## Roles and API Validation
Public registration always creates `SUPPLIER`. Product create/update/delete require `ADMIN`; both `ADMIN` and `SUPPLIER` can create movements. Unauthenticated requests return 401 and authenticated users lacking a required role receive 403. Products are currently a shared catalog, not supplier-owned.

Relevant routes:
- `POST /api/auth/register`, `POST /api/auth/login`
- `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/sku/{sku}`
- ADMIN: `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}`
- `POST /api/movements`, `GET /api/movements?productId={id}`
- `GET /api/products/{id}/analytics?from={date}&to={date}`
- `GET /api/products/reorder-suggestions`, `GET /api/movements/anomalies`
- `POST /api/ai/chat`

From `backend`, run `mvn test`. It requires a reachable MySQL database and `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` in the process environment. From `frontend`, run `npm test` and `npm run build`. Browser E2E recording is not verified here; never use a production database for test mutations.

## Database Hosting: AWS RDS MySQL
1. Provision a managed MySQL 8.4 RDS instance in a region reachable from the Render service. Enable automated backups and TLS.
2. Keep inbound access restricted. Allow TCP 3306 only from the Render service’s current outbound IP ranges as shown in its dashboard; do not open MySQL to `0.0.0.0/0`.
3. Record the RDS endpoint, database name, username, and password in a secret manager or the Render environment settings. Do not put values in this file or source control.
4. Set `DB_URL` to the provider’s TLS-enabled JDBC URL, for example `jdbc:mysql://RDS_HOST:3306/stocksight?serverTimezone=UTC&sslMode=VERIFY_IDENTITY`. Confirm the driver/provider TLS instructions before production.

## Render Backend
1. Create a Render Web Service from the repository using Docker and `backend` as its root directory/build context.
2. Set `SPRING_PROFILES_ACTIVE=prod` and `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `FRONTEND_ORIGIN` in Render’s secret environment settings. Set `JWT_SECRET` to a unique random value of at least 32 bytes.
3. Set `FRONTEND_ORIGIN` to the exact Vercel production origin. Add optional `ADMIN_USERNAME`/`ADMIN_PASSWORD` only for a controlled initial admin bootstrap; do not expose public admin registration.
4. Add `LLM_API_URL`, `LLM_API_KEY`, and `LLM_MODEL` only when using a compatible configured provider. Keep the API key in Render’s secret settings.
5. Use `/actuator/health` as the service health path. Wait for Flyway and Hibernate validation to succeed before sending traffic. Swagger/OpenAPI is disabled by default in the production profile.
6. Verify login, 401/403 behavior, product reads, and safe test-environment stock workflows against the deployed URL. Cloud deployment is not complete until these are run by the operator.

## Vercel Frontend
1. Import the repository into Vercel and set Root Directory to `frontend`.
2. Build command: `npm run build`; output directory: `dist`.
3. Set `VITE_API_BASE_URL` to `https://YOUR-RENDER-SERVICE.onrender.com/api` for Production and to the matching backend for Preview environments. Production builds intentionally do not fall back to localhost.
4. Deploy the frontend, then configure Render `FRONTEND_ORIGIN` to the exact deployed origin and redeploy the backend.
5. Confirm direct loads of `/login`, `/products`, and `/products/{id}` work through the existing SPA rewrite in `frontend/vercel.json`.

## Security and Known Limits
- No cloud deployment has been performed or verified here.
- Local Compose requires a user-created ignored `.env`; there are no credential defaults in Compose.
- `prod` disables Swagger/OpenAPI by default. Set `SWAGGER_ENABLED=true` only for a protected deployment or temporary controlled diagnostics.
- Rotate any credentials that may have been used before the Compose hardening, including the initial admin and database account credentials. Use the target database provider's documented rotation process.
- The Vite API URL is build-time configuration; changing it requires a new frontend build/deployment.
- JWTs are stored in `localStorage`; assess XSS exposure and consider a Secure, HttpOnly cookie design before production.
- Supplier isolation is not available because the current schema has no product owner relationship.
- Reorder recommendations have no lead-time input. Less than three outward transactions is reported as insufficient history.
- Anomalies are explainable rules, not a trained ML model. Assistant retrieval is lexical, not vector/embedding RAG; verify the chosen external model/provider before claiming live model responses.
