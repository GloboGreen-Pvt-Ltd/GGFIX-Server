# Ports and CORS (E2E)

## Backend ports (default)

| Service           | Port | Base path   | Use in admin / mobile |
|-------------------|------|-------------|------------------------|
| auth-service      | 8081 | `/auth`     | `NEXT_PUBLIC_AUTH_BASE` / `AUTH_BASE` |
| master-data       | 8091 | `/master`   | `NEXT_PUBLIC_API_BASE` / `MASTER_BASE` |
| ticket-service    | 8082 | `/tickets`  | `NEXT_PUBLIC_TICKET_BASE` / `TICKET_BASE` |

Override with env `PORT` (e.g. `PORT=9081`) when running a service.

## CORS

All three services allow:

- **Origins:** `http://localhost:3000`, `http://127.0.0.1:3000`, localhost/127.0.0.1 on 8081, 8082, 8083, 8091, 19006, 19000, and `*` (any origin for mobile / Expo).
- **Methods:** GET, POST, PUT, PATCH, DELETE, OPTIONS.
- **Headers:** `*` (including `Authorization` for JWT).
- **Preflight cache:** 3600 seconds.

If you still see CORS errors, ensure the client is calling the correct base URL (port) and that the backend is running with profile `dev` (no extra CORS restrictions).
