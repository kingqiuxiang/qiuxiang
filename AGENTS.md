# AGENTS.md

## Cursor Cloud specific instructions

灵测 LingCe is a single TypeScript product with two runnable services, orchestrated from the root `package.json` (npm; no monorepo tooling). Standard commands live in `README.md` and the `package.json` files — reference those rather than duplicating.

- **Services**
  - Backend: Express API on `:8787`, run via `tsx watch` (no build step for dev). Entry `server/src/index.ts`. Persistence is a local JSON file at `server/data/db.json` (auto-created; no database).
  - Frontend: Vite + React on `:5173`. `web/vite.config.ts` proxies `/api` → `http://localhost:8787`, so run both together and use `http://localhost:5173` in dev.

- **Run**: `npm run dev` (from repo root) starts both services concurrently. `npm run dev:server` / `npm run dev:web` run them individually. In production (`npm start`) the backend serves the built `web/dist`, so only `:8787` is used — this means `npm start` requires `npm run build` to have run first.

- **Demo mode is the default with zero config**: when YAPI/AI env vars (or in-app project settings) are absent, the platform serves built-in mock interfaces and heuristic AI param-fill/review. End-to-end flows (create project → AI fill → run test → AI review → batch auto-test) all work without any external services or secrets. Note: test runs show network errors / low scores in demo mode because there is no real target backend to hit — this is expected, not a bug.

- **Checks**: there is no lint script. Use `npm --prefix server run typecheck` (`tsc --noEmit`) for the backend and `npm run build` (runs `tsc -b && vite build`) for the frontend as the type/compile checks. There are no automated tests in this repo.

- **Optional (not installed by default)**: Playwright/Chromium for real-browser page checks (`cd server && npm i playwright && npx playwright install chromium`); a real YAPI instance and an OpenAI-compatible LLM endpoint for non-demo usage.
