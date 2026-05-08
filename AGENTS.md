# AGENTS.md

This file provides guidance to the AI agent when working with code in this repository.

## Project Overview

PaiAgent is a visual AI workflow orchestration platform with a React Flow drag-and-drop canvas (frontend) and a Spring Boot backend that executes DAG/LangGraph workflows with LLM and TTS nodes.

## Development Workflow

- **Development is Docker-only**: use `docker compose up -d` to run the full stack locally
- Frontend: http://localhost:8080, Backend API: http://localhost:8081
- Default login: `admin` / `admin123`

## Build Commands

**Backend (Maven, Java 21):**
- Build: `cd backend && mvn package -DskipTests -B`
- Clean: `cd backend && mvn clean`

**Frontend (npm, TypeScript + Vite):**
- Build: `cd frontend && npm run build` (runs `tsc && vite build`)
- Lint: `cd frontend && npm run lint`

## Required Environment Variables

At least one LLM API key must be set for the backend to function:
- `DEEPSEEK_API_KEY` and/or `QWEN_API_KEY` (required)
- `CHATGLM_API_KEY`, `AIPING_API_KEY` (optional)
- `TTS_API_KEY` (optional)
- `ENGINE_TYPE=dag` (default) or `langgraph`

## Architecture Notes

- **Dual workflow engine**: `DagWorkflowEngine` (Kahn's algorithm, strict DAG) and `LangGraphWorkflowEngine` (StateGraph, supports cycles). Switched via `ENGINE_TYPE` env var.
- **LLM abstraction**: `SpringAiChatService` uses Spring AI's `OpenAiChatModel` to route across multiple providers (DeepSeek, Qwen, ChatGLM, AIPing).
- **Frontend state**: Zustand store manages workflow definitions and execution state.
- **Auth**: Spring Security 6 + JWT (`JwtTokenProvider` + `JwtAuthFilter`), default credentials initialized in `DataInitConfig`.

## Code Style

- **Frontend**: TypeScript strict mode, `noUnusedLocals` and `noUnusedParameters` enabled. ESLint configured via `package.json` script (no standalone config file yet).
- **Backend**: Lombok for boilerplate reduction (excluded from final JAR).

## Testing

- No test framework is currently set up for either frontend or backend.
- To test the full system, run `docker compose up --build` and verify via the UI at http://localhost:8080.

## Important Patterns

- Node executors live in `backend/src/main/java/com/paiagent/engine/executors/` — read the relevant executor before modifying node behavior.
- Frontend components in `frontend/src/components/` map to canvas nodes, config panels, and debug drawers — read the component before editing.
- Use `@path/to/file` references when referring to existing code in conversations.
