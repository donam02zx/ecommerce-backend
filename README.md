# E-commerce Backend — VietProDev Challenge

Backend E-commerce Core (Java 17, Spring Boot 3.x, PostgreSQL, Swagger).

## Run

1. Create database: `CREATE DATABASE ecommerce_db;`
2. Copy `application-local.yml.example` → `application-local.yml` and set PostgreSQL password
3. Run `EcommerceBackendApplication` in IntelliJ

## Challenge 0 — Health API

```http
GET http://localhost:8081/api/health
```

## Swagger

- http://localhost:8081/swagger-ui.html
- http://localhost:8081/v3/api-docs

## Git workflow

- Branch: `feat/challenge_00_project_setup`
- PR into: `develop`
