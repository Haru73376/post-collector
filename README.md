# post-collector
A Spring Boot REST API to collect and organize social media posts by category and tag

## Overview
Post Collector is a REST API for managing saved social media posts 
from platforms like Instagram, YouTube, and Pinterest.
Users can organize posts by hierarchical categories and tags, 
with JWT-based authentication and soft delete support.

## Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Security**: Spring Security + JWT (Access Token + Refresh Token)
- **Database**: MySQL 8
- **ORM**: Spring Data JPA / Hibernate
- **Build**: Maven
- **Infrastructure**: Docker / Docker Compose
- **Documentation**: Swagger UI (SpringDoc OpenAPI)
- **Deployment**: Railway

- ## Features
- User registration and authentication (JWT + HttpOnly Cookie)
- Hierarchical category management (up to 3 levels)
- Save and organize social media post URLs with title, memo, and thumbnail
- Tag-based cross-category filtering
- Pagination, filtering, and keyword search
- Soft delete for saved posts
- Rate limiting on authentication endpoints
