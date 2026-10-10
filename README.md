# StockSight

StockSight is an inventory management and anomaly-monitoring application for supplier workflows. It tracks product balances from an opening balance plus recorded inward/outward movements, reports threshold alerts, calculates historical consumption/reorder suggestions, and flags explainable movement anomalies.

## Features
- JWT login and supplier registration; registration always creates `SUPPLIER` users.
- `ADMIN` product create/update/delete; authenticated users can read products.
- Inward/outward movements with ledger-derived stock, validation against negative stock, and product movement history.
- Dashboard totals and low-stock alerts derived from product API responses.
- Date-window consumption analysis, reorder suggestions, and rule-based anomaly events.
- Inventory assistant with lexical retrieval over live product, movement, reorder, and anomaly records. It calls a configured chat-completion provider when available and reports unavailable-provider/context conditions.
- Swagger/OpenAPI documentation and Docker Compose development services.

## Technology and Architecture
- Frontend: React 19, Vite, React Router, Axios.
- Backend: Java 21, Spring Boot 3.4, Spring Security, JWT, BCrypt, Spring Data JPA, MySQL 8.4, Flyway, Actuator, Springdoc OpenAPI.
- Persistence: MySQL. `products.current_stock` is recomputed from `opening_balance + net movement`; `safety_threshold` is the low-stock minimum. Flyway applies migrations on fresh schemas and baselines the existing non-empty schema at version 1.
- Authentication is stateless. Product writes require `ADMIN`; movement writes allow `ADMIN` and `SUPPLIER`. The current catalog is shared; products are not supplier-owned.

## Prerequisites
- JDK 21 or newer and Maven 3.9+ for backend development.
- Node.js 20+ and npm for frontend development.
- Docker Desktop for the Compose setup, or a reachable MySQL 8.4 database for manual runs.

## Local Setup (Windows PowerShell)
1. From the repository root, create a local environment file and edit it with unique local-only values:
	```powershell
	if (-not (Test-Path .env)) { Copy-Item .env.example .env }
	notepad .env
	```
	Set `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, and a `JWT_SECRET` with at least 32 random bytes. Keep `.env` untracked. `ADMIN_USERNAME` and `ADMIN_PASSWORD` are optional; when both are supplied, a first admin is created only if that username does not already exist.
2. Start the backend and MySQL:
	```powershell
	docker compose up --build -d
	docker compose ps
	```
	MySQL data persists in the named `stocksight_mysql` volume. The API health endpoint is `http://localhost:8080/actuator/health`.
3. Start the frontend in a second terminal:
	```powershell
	Set-Location .\frontend
	if (-not (Test-Path .env)) { Copy-Item .env.example .env }
	npm install
	npm run dev
	```
4. Open `http://localhost:5173`. The local Vite default API URL is `http://localhost:8080/api`; set `VITE_API_BASE_URL` to override it.
5. Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

The active backend Docker build uses `backend/src`; the legacy `backend/bin` snapshot is not used by the root Compose build.

## Environment Variables
| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL; Compose sets the service hostname, local manual runs can use `localhost:3307`. |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL application credentials. |
| `MYSQL_ROOT_PASSWORD` | Local Compose MySQL root password. |
| `JWT_SECRET` | HMAC signing secret, at least 32 bytes. |
| `JWT_EXPIRATION_MS` | JWT lifetime; defaults to 24 hours. |
| `FRONTEND_ORIGIN` | Exact browser origin allowed by backend CORS. |
| `SPRING_PROFILES_ACTIVE` | `dev` for local development; Compose/production uses `prod`. |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Optional one-time initial admin creation; never expose through public registration. |
| `LLM_API_URL`, `LLM_API_KEY`, `LLM_MODEL` | Optional OpenAI-compatible chat-completion provider settings. Keep the key in a secret manager. |
| `VITE_API_BASE_URL` | Frontend API base URL, including `/api`; required for production frontend builds. |

## Tests and Builds
Backend tests require a reachable MySQL database and these environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.
```powershell
Set-Location .\backend
mvn test
```

Frontend tests and production build:
```powershell
Set-Location .\frontend
npm test
npm run build
```

The API tests cover JWT expiry, BCrypt registration, ADMIN/SUPPLIER authorization, product CRUD, movements, stock reconciliation, date-window analytics, reorder rules, anomalies, CORS, and local-mock RAG behavior. The frontend unit tests cover auth session storage/logout and protected-route behavior. Browser E2E recording remains unverified because the Playwright author agent is unavailable in this workspace; direct frontend tests, production build, and HTTP readiness were checked independently.

## API Reference
- `POST /api/auth/register`, `POST /api/auth/login`
- `GET /api/products?page=0&size=10&q=...`, `GET /api/products/{id}`, `GET /api/products/sku/{sku}`
- `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}` (ADMIN only)
- `GET /api/products/{id}/analytics?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/products/reorder-suggestions?from=...&to=...`
- `GET /api/movements?productId=...`, `POST /api/movements`
- `GET /api/movements/anomalies?productId=...&from=...&to=...`
- `POST /api/ai/chat` with `{"question":"..."}`
- Swagger UI: `/swagger-ui/index.html`; health: `/actuator/health`.

## Deployment
See [DEPLOYMENT.md](DEPLOYMENT.md) for Vercel frontend, Render backend, and managed AWS RDS MySQL preparation. Cloud deployment has not been performed or verified by this repository workflow.

## Known Limitations
- Reorder calculations use actual outward movements and the safety threshold; lead time is unavailable because the schema has no lead-time field. Fewer than three outward transactions is treated as insufficient consumption history.
- Anomaly detection is a transparent rules baseline, not a trained ML model.
- Assistant retrieval is lexical ranking over live records, not vector/embedding RAG. A real external-model response has not been verified in this environment.
- Suppliers share the current catalog because there is no supplier ownership relationship in the schema. Do not claim per-supplier isolation.
- JWTs are stored in browser `localStorage`; production deployment should assess XSS risk and an HttpOnly Secure cookie/session design.
