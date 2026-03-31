---
name: base66-testing
description: Runbook for testing the Base66 application — Docker rebuild, auth, API endpoints, skill creation verification, and debugging.
---

# Base66 Testing Runbook

Base66 is a Spring Boot application that wraps OpenCode (an AI coding agent) and runs in Docker. It provides per-user workspaces, JWT authentication, chat, session management, permission handling, and skill creation via REST APIs.

---

## Docker Rebuild

Always use this sequence when rebuilding after code or config changes:

```bash
docker compose down
docker compose up --build --force-recreate -d
```

To wipe all persistent data (workspaces, OpenCode sessions) and start fresh:

```bash
docker compose down -v
docker compose up --build --force-recreate -d
```

**Warning**: `down -v` destroys the `base66-workspaces` and `base66-opencode-data` named volumes. All user workspace files and OpenCode session history will be lost.

---

## Startup Verification

After starting the container, check readiness:

```bash
docker compose logs -f
```

Look for this log line — it means the app is fully ready:

```
Base66 is fully initialized and ready to use at http://localhost:8080
```

Other key startup lines to confirm:

```
[opencode] opencode server listening on http://127.0.0.1:4096
Initialized git repository in workspace /data/workspaces/user1
Initialized git repository in workspace /data/workspaces/user2
Workspace ready for user=user1 path=/data/workspaces/user1
Workspace ready for user=user2 path=/data/workspaces/user2
```

Quick health check (the login endpoint is public — a 401 means the app is running):

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"x","password":"x"}'
# Expected: 401
```

---

## User Accounts

| Username | Password | Agent Name    |
|----------|----------|---------------|
| user1    | pass1    | user1-agent   |
| user2    | pass2    | user2-agent   |

Each user has a workspace at `/data/workspaces/<username>/` inside the container.

---

## Authentication

All `/api/base66/**` endpoints require a JWT Bearer token.

### Step 1: Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user2", "password": "pass2"}'
```

Response:

```json
{
  "username": "user2",
  "token": "<JWT_TOKEN>",
  "tokenType": "Bearer",
  "agentName": "user2-agent"
}
```

### Step 2: Use the token

Add this header to all subsequent requests:

```
Authorization: Bearer <JWT_TOKEN>
```

Tokens expire after 3600 seconds (1 hour). If you get a 401 on a previously working token, re-login.

---

## Testing Skill Creation

This is the primary test flow for verifying that OpenCode can write files in user workspaces with the correct permissions.

### Endpoint

```
POST /api/base66/skills/from-text
```

### Request

```bash
curl -s -X POST http://localhost:8080/api/base66/skills/from-text \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"skillName": "test-skill", "content": "A skill that does something useful for testing purposes."}'
```

**Important**: The field names are `skillName` and `content`. Using `freeText` or other names will return 400 Bad Request.

### Validation rules

- `skillName`: required, must match `[a-zA-Z0-9._-]+` (no spaces or special characters)
- `content`: required, free-text description of the skill

### Expected success response

```json
{
  "success": true,
  "path": ".opencode/skills/test-skill/SKILL.md",
  "message": "Skill created successfully."
}
```

### Possible failure responses

| Message | Meaning |
|---------|---------|
| `OpenCode did not return a skill path.` | The AI agent didn't produce the expected `SKILL_PATH:` output line |
| `OpenCode returned an invalid skill path.` | The path didn't match the safe pattern `\.opencode/skills/[a-zA-Z0-9._-]+/SKILL\.md` |
| `OpenCode returned a path that does not match the requested skill name.` | The AI wrote to a different skill name than requested |
| `Generated SKILL.md content is invalid or missing required frontmatter.` | File exists but missing `name:` or `description:` in YAML frontmatter |
| `OpenCode did not create SKILL.md in workspace.` | The file was not found on disk after the AI responded |

---

## Verifying Files in the Container

**Critical**: On Windows with Git Bash, Docker exec path arguments get mangled (e.g., `/data/` becomes `C:/Program Files/Git/data/`). Always wrap commands in `sh -c` to avoid this.

### Read a skill file

```bash
docker compose exec base66 sh -c "cat /data/workspaces/user2/.opencode/skills/test-skill/SKILL.md"
```

### List user's skills

```bash
docker compose exec base66 sh -c "ls -la /data/workspaces/user2/.opencode/skills/"
```

### List full workspace structure

```bash
docker compose exec base66 sh -c "ls -laR /data/workspaces/user2/.opencode/"
```

### Check if git repo exists in workspace

```bash
docker compose exec base66 sh -c "ls -la /data/workspaces/user2/.git"
```

### Check global config

```bash
docker compose exec base66 sh -c "cat /home/base66/.config/opencode/opencode.json"
```

### Check per-user config

```bash
docker compose exec base66 sh -c "cat /data/workspaces/user2/opencode.json"
```

---

## All API Endpoints

All `/api/base66/**` endpoints require `Authorization: Bearer <token>`.

| Method | Path | Auth | Request Body | Description |
|--------|------|------|-------------|-------------|
| POST | `/api/auth/login` | No | `{"username","password"}` | Get JWT token |
| POST | `/api/base66/chat` | Yes | `{"message":"..."}` | Send message to OpenCode agent |
| GET | `/api/base66/sessions?limit=20` | Yes | — | List sessions (limit 1-100, default 20) |
| POST | `/api/base66/sessions` | Yes | `{"title":"..."}` (optional) | Create new session |
| POST | `/api/base66/sessions/{id}/select` | Yes | — | Select/activate a session |
| PATCH | `/api/base66/sessions/{id}` | Yes | `{"title":"..."}` | Rename a session |
| DELETE | `/api/base66/sessions/{id}` | Yes | — | Delete a session |
| GET | `/api/base66/permissions/pending` | Yes | — | List pending permission requests |
| POST | `/api/base66/permissions/{id}/reply` | Yes | `{"reply":"once\|always\|reject"}` | Reply to a permission request |
| POST | `/api/base66/skills/from-text` | Yes | `{"skillName","content"}` | Create a skill from free text |

---

## Checking Logs

```bash
# Follow all logs in real time
docker compose logs -f

# Last 100 lines only
docker compose logs --tail=100 base66

# Logs since a specific time
docker compose logs --since="2026-04-01T00:00:00" base66
```

### Key log patterns

| Pattern | Meaning |
|---------|---------|
| `Base66 is fully initialized and ready to use` | App is ready |
| `[opencode] opencode server listening on` | OpenCode process started |
| `Initialized git repository in workspace` | Git repo created in user workspace |
| `Workspace ready for user=` | Workspace provisioning complete |
| `Incoming skill creation request from-text` | Skill creation endpoint was called |
| `Skill created successfully` | Skill file was written and verified |
| `Skill creation failed` | Something went wrong — check the reason field |
| `Incoming /api/base66/chat request` | Chat endpoint called |
| `Login attempt` / `Login success` / `Login failed` | Auth events |
| `OpenCode permission requested` | An OpenCode tool call needs permission approval |

---

## Infrastructure Reference

### Ports

| Port | Purpose | Exposed to host? |
|------|---------|-----------------|
| 8080 | Spring Boot HTTP (all REST APIs) | Yes |
| 4096 | OpenCode serve (internal) | No (container-internal only) |

### Named Docker Volumes

| Volume | Container Mount | Purpose |
|--------|----------------|---------|
| `base66-workspaces` | `/data/workspaces` | User workspace files (skills, browser data, etc.) |
| `base66-opencode-data` | `/home/base66/.local/share/opencode` | OpenCode sessions, logs, auth data |

### Bind Mounts (host -> container)

| Host Path | Container Path | Purpose |
|-----------|---------------|---------|
| `./mounts/global-config/` | `/home/base66/.config/opencode/` | Global OpenCode config + global skills |
| `./mounts/user1-config/opencode.json` | `/data/workspaces/user1/opencode.json` | Per-user1 workspace config |
| `./mounts/user2-config/opencode.json` | `/data/workspaces/user2/opencode.json` | Per-user2 workspace config |

### Global permission config (`mounts/global-config/opencode.json`)

Denies all file operations globally, except within `.opencode/skills/*`:

```json
{
  "permission": {
    "read":  { "*": "deny", ".opencode/skills/*": "allow" },
    "edit":  { "*": "deny", ".opencode/skills/*": "allow" },
    "glob":  { "*": "deny", ".opencode/skills/*": "allow" },
    "list":  { "*": "deny", ".opencode/skills": "allow", ".opencode/skills/*": "allow" },
    "bash":  "deny"
  }
}
```

OpenCode's `*` wildcard matches any character including `/`, so `.opencode/skills/*` matches `.opencode/skills/btc/SKILL.md`.

Permission patterns are matched against **relative paths** (relative to the git worktree root of the user's workspace). This is why each workspace needs a git repo initialized via `git init` — without it, `Instance.worktree` resolution fails and patterns don't match.

---

## Common Failures and Debugging

### 401 Unauthorized

- Wrong credentials in login request
- Expired JWT token (re-login after 1 hour)
- Missing or malformed `Authorization: Bearer <token>` header

### 400 Bad Request

- Wrong field names in request body (e.g., `freeText` instead of `content`, `name` instead of `skillName`)
- Missing required fields (`skillName` or `content` is blank)
- Invalid `skillName` characters (must be `[a-zA-Z0-9._-]+`)

### Permission denied in OpenCode (skill creation fails)

1. Check that the global config has the allow rules for `.opencode/skills/*`
2. Verify the git repo exists in the workspace: `docker compose exec base66 sh -c "ls /data/workspaces/user2/.git"`
3. If `.git` is missing, the `git init` step in `UserWorkspaceService.initializeGitRepoIfMissing()` may have failed — check logs for `"git init failed"` or `"Failed to run git init"`
4. Verify the per-user config doesn't override permission rules that conflict with the global config

### OpenCode not running

- Check logs for `[opencode] opencode server listening on` — if absent, OpenCode didn't start
- Look for `OpenCode process exited` or `Failed to start OpenCode` in logs
- Verify `OPENCODE_AUTOSTART=true` in `.env`

### File created but verification fails

- The AI might have written incorrect frontmatter — check the file content with `docker compose exec base66 sh -c "cat ..."`
- The SKILL.md must contain both `name: <skillName>` and `description: <something>` in YAML frontmatter
- The AI might have written to a different path than expected — check logs for the `SKILL_PATH:` line in the model response

### Config changes not taking effect

- Bind-mounted files (per-user `opencode.json`) reflect host changes immediately
- Global config changes require container restart: `docker compose down && docker compose up -d`
- Java code changes require full rebuild: `docker compose down && docker compose up --build --force-recreate -d`
