---
name: docker-test
description: Build and start the full Docker Compose stack to test the complete system end-to-end.
---

Run this skill to test the full PaiAgent system via Docker Compose.

## Steps

1. Run `docker compose up --build -d` from the project root
2. Wait for containers to be healthy (check with `docker compose ps`)
3. Verify the frontend is accessible at http://localhost:8080
4. Verify the backend API responds at http://localhost:8081
5. If any container fails to start or respond, report the logs via `docker compose logs <service>`
6. When done testing, remind the user they can stop with `docker compose down`

Note: Environment variables (API keys) must be set for the backend to function properly with LLM calls.
