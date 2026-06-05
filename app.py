from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from ai_assistant.models import (
    ApiTestRequest,
    ApiTestResponse,
    AutofillRequest,
    AutofillResponse,
    QuickStartRequest,
    QuickStartResponse,
    YapiImportRequest,
    YapiImportResponse,
)
from ai_assistant.services.param_filler import autofill_params
from ai_assistant.services.process_manager import process_manager
from ai_assistant.services.test_runner import run_api_test
from ai_assistant.services.yapi_parser import load_yapi_source


app = FastAPI(title="AI YAPI Test Assistant", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.post("/api/yapi/import", response_model=YapiImportResponse)
def import_yapi(req: YapiImportRequest) -> YapiImportResponse:
    try:
        endpoints = load_yapi_source(req.source, req.is_url)
    except Exception as exc:  # pragma: no cover - simple API wrapper
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return YapiImportResponse(endpoints=endpoints)


@app.post("/api/params/autofill", response_model=AutofillResponse)
def fill_params(req: AutofillRequest) -> AutofillResponse:
    return autofill_params(req.endpoint, req.project_root, req.extra_prompt)


@app.post("/api/project/quick-start", response_model=QuickStartResponse)
def quick_start(req: QuickStartRequest) -> QuickStartResponse:
    running = process_manager.start(command=req.command, cwd=req.cwd)
    return QuickStartResponse(pid=running.process.pid, command=running.command, cwd=running.cwd)


@app.get("/api/project/status")
def quick_status() -> dict[str, str | int | None]:
    return process_manager.status()


@app.post("/api/tests/run", response_model=ApiTestResponse)
def execute_tests(req: ApiTestRequest) -> ApiTestResponse:
    try:
        return run_api_test(
            base_url=req.base_url,
            endpoint=req.endpoint,
            query=req.query,
            path=req.path,
            body=req.body,
            frontend_url=req.frontend_url,
        )
    except Exception as exc:  # pragma: no cover
        raise HTTPException(status_code=400, detail=str(exc)) from exc


static_dir = Path(__file__).parent / "static"
if static_dir.exists():
    app.mount("/", StaticFiles(directory=str(static_dir), html=True), name="static")
