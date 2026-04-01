# Base66

Base66 is a Spring Boot application with a built-in web UI for authenticated chat via OpenCode. It includes JWT login, per-user workspace provisioning, chat sessions, permission handling, and skill creation from free text.

## Features

- JWT-based authentication
- Chat endpoint backed by OpenCode
- Session APIs: list, create, select, rename, delete
- Pending permission polling and reply flow
- Skill creation endpoint that writes SKILL.md files in user workspace
- Optional OpenCode auto-start on application startup
- Static frontend served from `/`

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Security (JWT)
- Spring Web + WebFlux
- Spring AI abstractions
- Maven Wrapper

## Prerequisites

- Java 21 installed
- OpenCode available locally or reachable via URL

## Configuration

Application properties load `.env` through:

- `spring.config.import=optional:file:.env[.properties]`

Required environment variables:

- `OPENCODE_BASE_URL`
- `APP_AUTH_JWT_SECRET`
- `APP_AUTH_JWT_EXPIRATION_SECONDS`

Optional OpenCode process variables:

- `OPENCODE_AUTOSTART` (default: `true`)
- `OPENCODE_COMMAND` (default: `opencode`)
- `OPENCODE_ARGS` (default: `serve --hostname 127.0.0.1 --port 4096`)
- `OPENCODE_WORKING_DIRECTORY` (default: empty)
- `OPENCODE_STARTUP_TIMEOUT_SECONDS` (default: `30`)
- `OPENCODE_STOP_ON_SHUTDOWN` (default: `true`)

### Example .env

```env
OPENCODE_BASE_URL=http://localhost:4096
APP_AUTH_JWT_SECRET=replace-with-a-strong-secret
APP_AUTH_JWT_EXPIRATION_SECONDS=3600
```

## Run Locally

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

Then open:

- http://localhost:8080

## Default Users

Configured in `application.properties`:

- `user1 / pass1`
- `user2 / pass2`

## Authentication

- `POST /api/auth/login` is public.
- All `/api/base66/**` endpoints require:
  - `Authorization: Bearer <token>`

### Login Example

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"pass1"}'
```

## API Endpoints

### Chat

- `POST /api/base66/chat`
  - Request: `{ "message": "..." }`
  - Response: `{ "response": "..." }`

### Sessions

- `GET /api/base66/sessions?limit=20`
- `POST /api/base66/sessions` with optional `{ "title": "..." }`
- `POST /api/base66/sessions/{sessionId}/select`
- `PATCH /api/base66/sessions/{sessionId}` with `{ "title": "..." }`
- `DELETE /api/base66/sessions/{sessionId}`

### Permissions

- `GET /api/base66/permissions/pending`
- `POST /api/base66/permissions/{requestId}/reply`
  - Request: `{ "reply": "once" | "always" | "reject" }`

### Skills

- `GET /api/base66/skills`
  - Response: `[{ "name": "...", "description": "..." }, ...]`

- `GET /api/base66/skills/{skillName}`
  - Response: `{ "name": "...", "description": "...", "content": "..." }`

- `POST /api/base66/skills`
  - Request: `{ "skillName": "...", "description": "...", "content": "..." }`
  - Response: `{ "name": "...", "description": "...", "content": "..." }`

- `PUT /api/base66/skills/{skillName}`
  - Request: `{ "description": "...", "content": "..." }`
  - Response: `{ "name": "...", "description": "...", "content": "..." }`

- `DELETE /api/base66/skills/{skillName}`
  - Response: `{ "success": true|false }`

## Error Behavior

- `401 Unauthorized`: missing/invalid/expired token
- `400 Bad Request`: validation or malformed request
- `502 Bad Gateway`: OpenCode returned an upstream error
- `503 Service Unavailable`: OpenCode endpoint not reachable

## Workspace Notes

- User workspaces are provisioned under `app.user-workspaces-root`.
- On startup, Base66 can:
  - Ensure OpenCode is running (when autostart is enabled)
  - Prepare configured user workspaces
  - Validate required per-user OpenCode agent setup

## Project Structure

- `src/main/java/com/akatsuki/base66/controller` - REST controllers
- `src/main/java/com/akatsuki/base66/service` - business logic and integrations
- `src/main/java/com/akatsuki/base66/security` - JWT security components
- `src/main/resources/application.properties` - app config
- `src/main/resources/static/index.html` - frontend UI
