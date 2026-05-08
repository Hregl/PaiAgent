---
name: verify
description: Verify that both frontend and backend build cleanly without errors. Runs TypeScript type checking and Maven compilation.
---

Run this skill to verify both the frontend and backend compile successfully after making changes.

## Steps

1. **Backend**: Run `mvn compile -f backend/pom.xml -B` to verify Java compilation
2. **Frontend**: Run `tsc --noEmit -p frontend/tsconfig.json` (or `cd frontend && npx tsc --noEmit`) to verify TypeScript types
3. Report any errors found. If both pass, confirm the build is clean.

Do not run the full Docker build — this is a quick compile/typecheck only.
