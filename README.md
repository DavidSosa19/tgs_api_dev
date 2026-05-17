# TGS API

REST API for a public transport operations management system. Built with Spring Boot, PostgreSQL, and JWT authentication.

## Tech Stack

- **Java 25** + **Spring Boot 4**
- **Spring Security** — JWT authentication (HS512), RBAC with granular permissions
- **Spring Data JPA** + **Hibernate 7**
- **PostgreSQL** (Neon in production)
- **Lombok**, **Maven**
- **SonarCloud** — continuous code quality analysis

## Features

- **Multi-tenancy** — every business entity is scoped to a `company_id`; queries are automatically filtered by the authenticated user's company via `TenantSpecifications`
- **RBAC** — roles (`ADMIN`, `USER`) contain granular permissions (`ROUTE_READ`, `VEHICLE_WRITE`, etc.); `@PreAuthorize` guards every endpoint
- **JWT** — access + refresh token pair; tenant context populated from the token on every request and cleared in a `finally` block
- **Soft delete** — entities are deactivated (`active = false`) instead of removed; `@SQLRestriction("active = true")` ensures deleted records are never returned

## Getting Started

### Prerequisites

- Java 25+
- PostgreSQL running locally (or a Neon connection string)
- Maven (or use the included `./mvnw` wrapper)

### Environment Variables

| Variable | Description | Default (dev only) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC connection string | `jdbc:postgresql://localhost:5432/tgs_dev` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `postgres` |
| `TGS_JWT_SECRET` | Base64-encoded HS512 secret key | *(required)* |
| `APP_CORS_ALLOWED_ORIGIN` | Frontend origin for CORS | `http://localhost:4200` |

> **Note:** `TGS_JWT_SECRET` has no fallback — the app will not start without it.  
> Generate one with: `openssl rand -base64 64`

Copy `src/main/resources/application.properties.example` → `application.properties` and fill in the values.

### Run locally

```bash
export TGS_JWT_SECRET=<your_base64_secret>
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### Database setup

Run migrations in order against your PostgreSQL instance:

```sql
\i src/main/resources/sql/V3__company_schema.sql
\i src/main/resources/sql/V4__schedule_and_operation_company.sql
\i src/main/resources/sql/V5__tenant_security_hardening.sql
```

Optionally seed a second company and admin user for multi-tenant testing:

```sql
\i src/main/resources/sql/seed_empresa2_usuario_admin.sql
```

## Project Structure

```
src/main/java/com/example/tgs_dev/
├── config/          # Security, CORS, JWT key config
├── controller/      # REST endpoints
│   ├── request/     # Request DTOs (validated)
│   └── response/    # Response DTOs
├── entity/          # JPA entities — all business entities carry company_id
├── mapper/          # Entity ↔ DTO mappers
├── repository/      # Spring Data repositories
│   ├── base/        # BaseRepository + softDelete + filter overloads
│   ├── filter/      # Dynamic filter (GenericSpecification)
│   └── specification/ # TenantSpecifications, CommonSpecifications
├── security/        # JWT filter, TenantContext (ThreadLocal)
└── service/         # Business logic — all reads scoped to current tenant
```

## API Overview

All endpoints require a Bearer JWT token except `/api/auth/**`.

| Method | Path | Permission |
|---|---|---|
| `POST` | `/api/auth/login` | Public |
| `POST` | `/api/auth/register` | Public |
| `GET` | `/api/auth/me` | Authenticated |
| `GET` | `/api/route` | `ROUTE_READ` |
| `POST/PUT/DELETE` | `/api/route` | `ROUTE_WRITE` |
| `GET` | `/api/vehicle` | `VEHICLE_READ` |
| `POST/PUT/DELETE` | `/api/vehicle` | `VEHICLE_WRITE` |
| `GET` | `/api/rotation` | `ROTATION_READ` |
| `POST/PUT/DELETE` | `/api/rotation` | `ROTATION_WRITE` |
| `GET` | `/api/schedule-template` | `SCHEDULE_TEMPLATE_READ` |
| `POST/PUT/DELETE` | `/api/schedule-template` | `SCHEDULE_TEMPLATE_WRITE` |
| `GET` | `/api/routeOperation/{date}` | `OPERATION_READ` |
| `POST/DELETE` | `/api/routeOperation` | `OPERATION_MANAGE` |

## Deployment

Deployed on **Render** (Web Service) connected to **Neon** (PostgreSQL).  
CI/CD via GitHub Actions: SonarCloud analysis → Docker build → push to GHCR → Render deploy hook.

Set all environment variables listed above in the Render dashboard under _Environment_.
