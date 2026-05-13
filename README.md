# TGS API

REST API for a public transport operations management system. Built with Spring Boot, PostgreSQL, and JWT authentication.

## Tech Stack

- **Java 25** + **Spring Boot 4**
- **Spring Security** — JWT authentication (HS512)
- **Spring Data JPA** + **Hibernate 6**
- **PostgreSQL** (Neon in production)
- **Lombok**, **Maven**

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

### Run locally

```bash
# Set required env var
export TGS_JWT_SECRET=<your_base64_secret>

# Build and run
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

## Project Structure

```
src/main/java/com/example/tgs_dev/
├── config/          # Security, CORS, JWT key config
├── controller/      # REST endpoints
│   ├── request/     # Request DTOs
│   └── response/    # Response DTOs
├── entity/          # JPA entities
├── repository/      # Spring Data repositories + base/filter/spec
├── security/        # JWT filter and service
└── service/         # Business logic
```

## API Overview

All endpoints require a Bearer JWT token except `/api/auth/**`.

| Method | Path | Role |
|---|---|---|
| `POST` | `/api/auth/login` | Public |
| `POST` | `/api/auth/register` | Public |
| `GET` | `/api/**` | ADMIN, USER |
| `POST/PUT/DELETE` | `/api/**` | ADMIN |

## Deployment

Deployed on **Render** (Web Service) connected to **Neon** (PostgreSQL).

Set all environment variables listed above in the Render dashboard under _Environment_.
