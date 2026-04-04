# 🔐 Timesheet Management — JWT Authentication & Authorization

A production-ready **Spring Boot 4.x** REST API implementing stateless JWT-based authentication and role-based access control (RBAC).

---

## 📋 Table of Contents

- [Tech Stack](#-tech-stack)
- [Features](#-features)
- [Project Structure](#-project-structure)
- [Database Design](#-database-design)
- [Security Architecture](#-security-architecture)
- [API Endpoints](#-api-endpoints)
- [DTOs & Payloads](#-dtos--payloads)
- [Configuration](#-configuration)
- [Running the Application](#-running-the-application)
- [Running Tests](#-running-tests)
- [Sample Requests](#-sample-requests)
- [Error Handling](#-error-handling)
- [Spring Boot 4.x Notes](#-spring-boot-4x--spring-security-7-notes)

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Language |
| Spring Boot | 4.0.5 | Framework |
| Spring Security | 7.x | Authentication & Authorization |
| Spring Data JPA | 4.x | ORM / Database access |
| MySQL | 8.x | Primary database |
| H2 | — | In-memory DB for tests |
| JJWT | 0.12.6 | JWT generation & validation |
| Lombok | latest | Boilerplate reduction |
| SpringDoc OpenAPI | 2.8.6 | Swagger UI documentation |
| Maven | 3.x | Build tool |

---

## ✨ Features

### ✅ User Registration
- `POST /api/auth/register`
- Accepts `username`, `email`, `password`, `role`
- Password encrypted with **BCrypt**
- Validates duplicate username/email — returns `409 Conflict`
- Bean Validation (`@Valid`) on all fields
- Auto-logs in and returns JWT immediately after registration

### ✅ User Login
- `POST /api/auth/login`
- Accepts **username OR email** + password
- Authenticates via Spring Security `AuthenticationManager`
- Returns **JWT access token** + **refresh token** on success

### ✅ Token Refresh
- `POST /api/auth/refresh`
- Exchanges a valid refresh token for a new access token
- Validates expiry — returns `403` if expired

### ✅ Role-Based Access Control
| Role | Access |
|------|--------|
| `ROLE_USER` | `/api/user/**` |
| `ROLE_ADMIN` | `/api/user/**` + `/api/admin/**` |

### ✅ JWT Implementation
- Signed with HMAC-SHA256 (256-bit secret)
- Claims include: `sub` (username), `roles`, `iat`, `exp`
- Access token TTL: **24 hours**
- Refresh token TTL: **7 days** (UUID stored in DB)
- Validated on every request via `JwtAuthFilter`

### ✅ Global Exception Handling
- Structured JSON error responses for all error types
- `400` validation errors, `401` unauthorized, `403` forbidden, `409` conflict

### ✅ Swagger / OpenAPI
- Auto-generated docs at `/swagger-ui.html`
- Bearer token auth integrated into Swagger UI

### ✅ Unit Tests
- 7 tests covering `AuthController` register/login flows
- H2 in-memory DB — no external DB required to run tests

---

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/timesheetManagement/
│   │   ├── TimesheetmanagementApplication.java   ← Entry point
│   │   │
│   │   ├── config/
│   │   │   ├── SecurityConfig.java               ← Security filter chain
│   │   │   ├── OpenAPIConfig.java                ← Swagger/OpenAPI config
│   │   │   └── DataInitializer.java              ← Seeds roles on startup
│   │   │
│   │   ├── controller/
│   │   │   ├── AuthController.java               ← /api/auth/**
│   │   │   ├── UserController.java               ← /api/user/**
│   │   │   └── AdminController.java              ← /api/admin/**
│   │   │
│   │   ├── dto/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── AuthResponse.java
│   │   │   └── RefreshTokenRequest.java
│   │   │
│   │   ├── entity/
│   │   │   ├── RoleName.java  (enum)             ← ROLE_USER, ROLE_ADMIN
│   │   │   ├── Role.java                         ← roles table
│   │   │   ├── User.java                         ← users table
│   │   │   └── RefreshToken.java                 ← refresh_tokens table
│   │   │
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java       ← @RestControllerAdvice
│   │   │   ├── UserAlreadyExistsException.java
│   │   │   ├── InvalidTokenException.java
│   │   │   └── ResourceNotFoundException.java
│   │   │
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── RoleRepository.java
│   │   │   └── RefreshTokenRepository.java
│   │   │
│   │   ├── security/
│   │   │   ├── JwtUtils.java                     ← Generate / validate JWT
│   │   │   ├── JwtAuthFilter.java                ← OncePerRequestFilter
│   │   │   ├── JwtAuthEntryPoint.java            ← 401 JSON handler
│   │   │   └── UserDetailsServiceImpl.java       ← Load user by username/email
│   │   │
│   │   └── service/
│   │       ├── AuthService.java                  ← Register + Login logic
│   │       ├── RefreshTokenService.java           ← Refresh token lifecycle
│   │       └── UserService.java                  ← User query helpers
│   │
│   └── resources/
│       ├── application.yaml                      ← App configuration
│       └── schema.sql                            ← Reference DDL (MySQL)
│
└── test/
    ├── java/com/timesheetManagement/
    │   ├── TimesheetmanagementApplicationTests.java ← Context load test
    │   └── controller/
    │       └── AuthControllerTest.java           ← 6 MockMvc auth tests
    └── resources/
        └── application.yaml                      ← H2 override for tests
```

---

## 🗄 Database Design

```
┌─────────────┐        ┌──────────────┐        ┌───────────────┐
│   users     │        │  user_roles  │        │    roles      │
│─────────────│        │──────────────│        │───────────────│
│ id (PK)     │◄──────►│ user_id (FK) │◄──────►│ id (PK)       │
│ username    │        │ role_id (FK) │        │ name (enum)   │
│ email       │        └──────────────┘        │  ROLE_USER    │
│ password    │                                │  ROLE_ADMIN   │
└──────┬──────┘                                └───────────────┘
       │ 1
       │
       │ 1
┌──────▼────────────┐
│  refresh_tokens   │
│───────────────────│
│ id (PK)           │
│ user_id (FK, UQ)  │
│ token (UUID, UQ)  │
│ expiry_date       │
└───────────────────┘
```

> Tables are **auto-created by Hibernate** (`ddl-auto: update`). The `schema.sql` file is a reference only.

---

## 🔒 Security Architecture

```
HTTP Request
    │
    ▼
┌──────────────────────────────────┐
│         JwtAuthFilter            │  ← Runs before every request
│  1. Extract Bearer token         │
│  2. JwtUtils.validateToken()     │
│  3. Load UserDetails from DB     │
│  4. Set SecurityContext          │
└──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────┐
│      SecurityFilterChain         │
│  /api/auth/**    → permitAll     │
│  /api/user/**    → USER, ADMIN   │
│  /api/admin/**   → ADMIN only    │
│  Swagger paths   → permitAll     │
└──────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────┐
│   Controller + @PreAuthorize     │
└──────────────────────────────────┘
```

**JWT Token Structure:**
```json
{
  "sub": "john_doe",
  "roles": ["ROLE_USER"],
  "iat": 1743535200,
  "exp": 1743621600
}
```

---

## 🌐 API Endpoints

### 🔓 Public (no token required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Login, receive JWT |
| `POST` | `/api/auth/refresh` | Refresh access token |
| `GET` | `/swagger-ui.html` | Swagger UI |
| `GET` | `/api-docs` | OpenAPI JSON |

### 🔐 Protected — USER or ADMIN

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/user/me` | Get own profile |
| `GET` | `/api/user/{id}` | Get user by ID |
| `GET` | `/api/user/dashboard` | User dashboard |

### 🔐 Protected — ADMIN only

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/dashboard` | Admin dashboard |
| `GET` | `/api/admin/users` | List all users |

---

## 📦 DTOs & Payloads

### RegisterRequest
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "mypassword",
  "role": "ROLE_USER"
}
```
> `role` is optional — defaults to `ROLE_USER` if omitted.

### LoginRequest
```json
{
  "usernameOrEmail": "john_doe",
  "password": "mypassword"
}
```

### AuthResponse
```json
{
  "accessToken": "<JWT>",
  "refreshToken": "<UUID>",
  "tokenType": "Bearer",
  "username": "john_doe",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

### RefreshTokenRequest
```json
{
  "refreshToken": "<UUID>"
}
```

---

## ⚙️ Configuration

**`src/main/resources/application.yaml`**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/timesheet_auth_db?createDatabaseIfNotExist=true
    username: root          # ← update this
    password: root          # ← update this

  jpa:
    hibernate:
      ddl-auto: update      # auto-creates/updates tables

jwt:
  secret: 5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437
  expiration: 86400000      # 24 hours (ms)
  refresh-expiration: 604800000  # 7 days (ms)
```

> ⚠️ **Change `jwt.secret`** to a unique 256-bit base64-encoded key in production!

---

## 🚀 Running the Application

### Prerequisites
- Java 21+
- Maven 3.x
- MySQL 8.x running locally

### Steps

```bash
# 1. Clone / open the project
cd timesheetmanagement

# 2. Update DB credentials in src/main/resources/application.yaml

# 3. Build
./mvnw clean package -DskipTests

# 4. Run
./mvnw spring-boot:run

# App starts on http://localhost:8080
# Swagger UI → http://localhost:8080/swagger-ui.html
```

---

## 🧪 Running Tests

> Tests use **H2 in-memory DB** — no MySQL required.

```bash
./mvnw test
```

**Test Results:**
```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0  ✅
BUILD SUCCESS
```

### Test Coverage

| Test | Scenario |
|------|----------|
| `register_validRequest_returns201` | Happy path registration |
| `register_blankUsername_returns400` | Validation: empty username |
| `register_invalidEmail_returns400` | Validation: bad email format |
| `register_shortPassword_returns400` | Validation: password < 6 chars |
| `login_validCredentials_returns200` | Happy path login |
| `login_emptyBody_returns400` | Validation: missing credentials |
| `contextLoads` | Spring context starts successfully |

---

## 🔁 Sample Requests

See **`API_SAMPLES.sh`** for full cURL examples, or use Swagger UI at `/swagger-ui.html`.

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"pass123"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"john","password":"pass123"}'

# Access protected route
curl http://localhost:8080/api/user/me \
  -H "Authorization: Bearer <access-token>"

# Admin route
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <admin-access-token>"
```

---

## ❌ Error Handling

All errors return a consistent JSON structure:

```json
{
  "timestamp": "2026-04-02T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "email": "Email should be valid",
    "password": "Password must be at least 6 characters"
  },
  "path": "/api/auth/register"
}
```

| HTTP Status | Scenario |
|-------------|----------|
| `201 Created` | Successful registration |
| `200 OK` | Successful login / token refresh |
| `400 Bad Request` | Bean validation failure |
| `401 Unauthorized` | Missing / invalid JWT |
| `403 Forbidden` | Valid JWT but insufficient role |
| `404 Not Found` | Resource not found |
| `409 Conflict` | Duplicate username or email |
| `500 Internal Server Error` | Unexpected server error |

---

## 📝 Spring Boot 4.x / Spring Security 7 Notes

This project targets **Spring Boot 4.0.5** which bundles **Spring Security 7.x**. Key differences from Spring Boot 3.x:

| Area | Spring Boot 3.x | Spring Boot 4.x (this project) |
|------|-----------------|--------------------------------|
| `DaoAuthenticationProvider` | `new DaoAuthenticationProvider()` + `.setUserDetailsService(...)` | `new DaoAuthenticationProvider(userDetailsService)` — setter removed |
| `@WebMvcTest` package | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| Web starter artifact | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Test starter artifact | `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` |
| JWT API (JJWT 0.12.x) | `parseClaimsJws()` | `parseSignedClaims()` |

---

## 👨‍💻 Author

**Timesheet Management Team**  
Built with Spring Boot 4.x · Java 21 · JWT · MySQL · April 2026

