# post-collector

A Spring Boot REST API to collect and organize social media posts by category and tag — no matter which platform they came from.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?logo=springsecurity&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Tests](https://img.shields.io/badge/Tests-335%20passing-brightgreen)
![Coverage](https://img.shields.io/badge/Coverage-97%25%20line%20%2F%2097%25%20branch-brightgreen)

**Live Demo**: https://post-collector-production.up.railway.app
**API Docs (Swagger UI)**: https://post-collector-production.up.railway.app/swagger-ui.html

---

## Overview

Post Collector is a REST API for saving and organizing posts from social media platforms (Instagram, YouTube, Pinterest, TikTok, X, and more) into hierarchical categories and cross-cutting tags — regardless of which platform each post came from.

### Why I built this

I frequently save posts and videos across several different SNS apps, and each app lets me organize what I saved *within that app* — but never *across* apps. That meant I'd often forget which app I'd saved something in ("was that reel on Instagram or was it a YouTube Short?"), and I had no way to pull up "everything I saved under this topic" across platforms at once. This project is my answer to that: a single place to save a post from any platform, tag and categorize it the same way regardless of source, and find it again later.

**Who it's for**: people who save posts often and lose track of which app they saved them in, and anyone who wants to manage saved content from multiple SNS platforms in one place.

---

## Features

- User registration and login (JWT access token + HttpOnly refresh token cookie)
- Hierarchical category management (up to 3 levels deep, with cycle/depth validation)
- Save posts with URL, title, memo, and thumbnail, tied to any supported platform
- Tag-based, cross-category filtering (a tag applies across categories, not within one)
- Pagination, multi-criteria filtering (category / platform / tag / favorite / keyword), and keyword search across title & memo
- Soft delete for saved posts (nothing is physically removed on delete)
- Rate limiting on authentication endpoints (IP-based) and authenticated APIs (per-user)

## Technical Highlights

Beyond CRUD, a few design decisions this project specifically works through:

- **IDOR-safe by construction**: every resource lookup is scoped by `(id, userId)` at the query level, not filtered after the fact — "not found" and "not yours" always return the same 404, so resource existence can't be probed by ID.
- **Rate limiting split across two mechanisms on purpose**: a `Filter` (`AuthRateLimitFilter`, IP-based) protects `/auth/login` and `/auth/register` *before* authentication exists, while a `HandlerInterceptor` (`UserRateLimitInterceptor`, per-user) protects authenticated endpoints *after* the security chain resolves an identity — chosen because a Filter runs outside `DispatcherServlet` (so it can't use `@RestControllerAdvice` for its error response) while an Interceptor runs inside it (so it can).
- **Soft delete via `@SQLRestriction`**: deleted posts are excluded from every query path automatically at the Hibernate level, rather than every custom query needing its own `deleted_at IS NULL` clause.
- **Refresh token rotation**: each `/auth/refresh` call deletes the presented token and issues a new one, so a stolen-but-unused refresh token becomes unusable the moment the legitimate owner refreshes.
- **Package-by-feature, not package-by-layer**: code is organized as `auth/`, `category/`, `tag/`, `post/`, `user/` (each containing its own Controller/Service/Repository/DTOs), rather than top-level `controller/`/`service/`/`repository/` packages — so changes to one feature stay contained to one package.

## Tech Stack

| Category | Choice | Why |
|---|---|---|
| Language | Java 21 | Records for DTOs, pattern-matching `instanceof` for cleaner null/type checks |
| Framework | Spring Boot 3.5 | |
| Security | Spring Security + JWT | Stateless auth fits a REST API; access token in the response body + refresh token in an HttpOnly cookie balances usability against XSS exposure |
| Database | MySQL 8 | Relational fit for hierarchical categories and many-to-many tag associations |
| ORM | Spring Data JPA / Hibernate | Productivity, paired with explicit attention to its sharp edges (dirty-checking/flush timing, N+1 avoidance via `@EntityGraph`, soft-delete via `@SQLRestriction`) |
| Rate limiting | Bucket4j | Lightweight token-bucket implementation; no external dependency (Redis, etc.) needed for a single-instance deployment |
| Testing | JUnit 5, Mockito, Testcontainers | Testcontainers runs tests against a real MySQL instance rather than H2, so tests exercise the actual SQL/constraints that will run in production |
| Coverage | JaCoCo | |
| API Docs | SpringDoc OpenAPI (Swagger UI) | |
| Infrastructure | Docker Compose | Reproducible local MySQL instance |
| Deployment | Railway | Dockerfile-based deploy; MySQL as a managed plugin service |

## Architecture

```mermaid
flowchart TB
    Client(["Client"])

    subgraph Security["Security Filter Chain"]
        RateLimit["AuthRateLimitFilter\n(IP-based, login/register only)"]
        JwtFilter["JwtAuthenticationFilter\n(parses Bearer JWT)"]
    end

    subgraph Web["Web Layer"]
        Interceptor["UserRateLimitInterceptor\n(per-user, authenticated APIs)"]
        Controller["Controllers"]
    end

    Service["Service Layer\n(business rules, IDOR scoping)"]
    Repository["Spring Data JPA Repositories"]
    DB[("MySQL 8")]

    Client --> RateLimit --> JwtFilter --> Interceptor --> Controller
    Controller --> Service --> Repository --> DB
```

### ER Diagram

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : issues
    USERS ||--o{ CATEGORIES : owns
    USERS ||--o{ SAVED_POSTS : owns
    USERS ||--o{ TAGS : owns
    CATEGORIES o|--o{ CATEGORIES : "parent of"
    CATEGORIES o|--o{ SAVED_POSTS : contains
    SAVED_POSTS ||--o{ POST_TAGS : has
    TAGS ||--o{ POST_TAGS : "applied via"

    USERS {
        uuid id PK
        string username UK
        string email UK
        string password_hash
        datetime created_at
        datetime updated_at
    }

    REFRESH_TOKENS {
        bigint id PK
        uuid user_id FK
        string token_hash UK "SHA-256 hash"
        datetime expires_at
        datetime created_at
    }

    CATEGORIES {
        uuid id PK
        uuid user_id FK
        uuid parent_id FK "nullable, NULL = root"
        string name
        int sort_order
        datetime created_at
        datetime updated_at
    }

    SAVED_POSTS {
        uuid id PK
        uuid user_id FK
        uuid category_id FK "nullable, NULL = uncategorized"
        string url "HTTPS required"
        string title
        text memo "nullable"
        string thumbnail_url "nullable, HTTPS required"
        string platform "INSTAGRAM/YOUTUBE/PINTEREST/TIKTOK/X/OTHER"
        boolean is_favorite
        datetime created_at
        datetime updated_at
        datetime deleted_at "nullable, soft delete"
    }

    TAGS {
        bigint id PK
        uuid user_id FK
        string name "unique per user"
        datetime created_at
    }

    POST_TAGS {
        uuid post_id PK,FK
        bigint tag_id PK,FK
    }
```

## Testing & Quality

Testing is split across four layers, each with a deliberately different scope so nothing is re-verified redundantly across layers:

| Layer | Tool | What it covers |
|---|---|---|
| Service | JUnit 5 + Mockito | Business rules and branch logic, with dependencies mocked |
| Repository | `@DataJpaTest` + Testcontainers | Custom queries and dynamic `Specification` filters against a real MySQL instance |
| Controller | `@WebMvcTest` + MockMvc | HTTP status codes, request validation, and Spring Security behavior |
| Integration | `@SpringBootTest` + Testcontainers | End-to-end flows through the full stack — real JWTs, cross-user access checks, and `@Transactional` rollback behavior — that no single layer above can verify on its own |

**335 tests** across 22 test classes, **~97% line / ~97% branch coverage** (enforced at 80% minimum via JaCoCo; run `./mvnw test` to see the current report at `target/site/jacoco/index.html`).

## API Example

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"password123"}'

# Log in
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'
# -> { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 900 }

# Save a post (with the access token from above)
curl -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://instagram.com/p/xyz","title":"Great recipe","platform":"INSTAGRAM"}'
```

Full interactive API documentation is available via Swagger UI once the app is running (see below).

## Getting Started

**Prerequisites**: Java 21, Docker

```bash
# 1. Clone and configure environment variables
git clone https://github.com/Haru73376/post-collector.git
cd post-collector
cp .env.example .env
# edit .env: set DB_USERNAME, DB_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET

# 2. Start MySQL
docker compose up -d

# 3. Run the app
./mvnw spring-boot:run

# 4. Open Swagger UI in your browser
# http://localhost:8080/swagger-ui.html
```

**Run the tests** (requires Docker, since Repository/Integration tests use Testcontainers):

```bash
./mvnw test
```