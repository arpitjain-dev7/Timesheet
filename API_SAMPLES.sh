# ─────────────────────────────────────────────────────────────────────────────
#  Timesheet Management – Auth API  |  Sample cURL & Postman Requests
#  Base URL: http://localhost:8080
#  Swagger UI: http://localhost:8080/swagger-ui.html
# ─────────────────────────────────────────────────────────────────────────────

# ══════════════════════════════════════════════════════════════════════════════
# 1. REGISTER a new USER
# ══════════════════════════════════════════════════════════════════════════════
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "password": "mypassword",
    "role": "ROLE_USER"
  }'

# Expected Response (201 Created):
# {
#   "accessToken": "<JWT>",
#   "refreshToken": "<UUID>",
#   "tokenType": "Bearer",
#   "username": "john_doe",
#   "email": "john@example.com",
#   "roles": ["ROLE_USER"]
# }

# ══════════════════════════════════════════════════════════════════════════════
# 2. REGISTER an ADMIN
# ══════════════════════════════════════════════════════════════════════════════
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin_user",
    "email": "admin@example.com",
    "password": "adminpassword",
    "role": "ROLE_ADMIN"
  }'

# ══════════════════════════════════════════════════════════════════════════════
# 3. LOGIN  (by username OR email)
# ══════════════════════════════════════════════════════════════════════════════
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "john_doe",
    "password": "mypassword"
  }'

# Login by email:
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "john@example.com",
    "password": "mypassword"
  }'

# ══════════════════════════════════════════════════════════════════════════════
# 4. REFRESH access token
# ══════════════════════════════════════════════════════════════════════════════
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<your-refresh-token-uuid>"
  }'

# ══════════════════════════════════════════════════════════════════════════════
# 5. USER endpoints  (requires ROLE_USER or ROLE_ADMIN)
# ══════════════════════════════════════════════════════════════════════════════

# 5a. Get my profile
curl -X GET http://localhost:8080/api/user/me \
  -H "Authorization: Bearer <your-access-token>"

# 5b. Get user by ID
curl -X GET http://localhost:8080/api/user/1 \
  -H "Authorization: Bearer <your-access-token>"

# 5c. User dashboard
curl -X GET http://localhost:8080/api/user/dashboard \
  -H "Authorization: Bearer <your-access-token>"

# ══════════════════════════════════════════════════════════════════════════════
# 6. ADMIN endpoints  (requires ROLE_ADMIN only)
# ══════════════════════════════════════════════════════════════════════════════

# 6a. Admin dashboard
curl -X GET http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer <your-admin-access-token>"

# 6b. List all users
curl -X GET http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <your-admin-access-token>"

# ══════════════════════════════════════════════════════════════════════════════
# EXPECTED ERROR RESPONSES
# ══════════════════════════════════════════════════════════════════════════════

# 401 Unauthorized – no/invalid token
# {
#   "status": 401,
#   "error": "Unauthorized",
#   "message": "Full authentication is required to access this resource",
#   "path": "/api/user/me"
# }

# 403 Forbidden – ROLE_USER accessing admin endpoint
# {
#   "timestamp": "...",
#   "status": 403,
#   "error": "Forbidden",
#   "message": "Access denied: insufficient permissions",
#   "path": "/api/admin/dashboard"
# }

# 409 Conflict – duplicate registration
# {
#   "timestamp": "...",
#   "status": 409,
#   "error": "Conflict",
#   "message": "Username 'john_doe' is already taken",
#   "path": "/api/auth/register"
# }

# 400 Bad Request – validation failure
# {
#   "timestamp": "...",
#   "status": 400,
#   "error": "Bad Request",
#   "message": "Validation failed",
#   "details": {
#     "email": "Email should be valid",
#     "password": "Password must be at least 6 characters"
#   },
#   "path": "/api/auth/register"
# }

