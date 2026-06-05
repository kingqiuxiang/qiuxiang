from __future__ import annotations

from pathlib import Path

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.schemas import (
    ApiTestRequest,
    FillParamsRequest,
    StartProjectRequest,
    StopProjectRequest,
    UiTestRequest,
    WorkflowRequest,
    YapiImportRequest,
)
from app.services.parameter_filler import fill_parameters
from app.services.project_runner import ProjectRunner
from app.services.tester import run_api_test, run_ui_smoke_test
from app.services.yapi_parser import parse_yapi_interfaces

app = FastAPI(title="AI YAPI Workflow System", version="0.1.0")
project_runner = ProjectRunner()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

STATIC_DIR = Path(__file__).parent / "static"
app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")


@app.get("/")
def home() -> FileResponse:
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/yapi/parse")
def yapi_parse(payload: YapiImportRequest) -> dict:
    yapi_json = payload.yapi_json
    if payload.source_type == "url":
        if not payload.yapi_url:
            raise HTTPException(status_code=400, detail="source_type=url 时必须提供 yapi_url")
        try:
            response = httpx.get(str(payload.yapi_url), timeout=20)
            response.raise_for_status()
            yapi_json = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise HTTPException(status_code=400, detail=f"读取 YAPI URL 失败: {exc}") from exc

    if not isinstance(yapi_json, dict):
        raise HTTPException(status_code=400, detail="请输入合法的 yapi_json")

    interfaces = parse_yapi_interfaces(yapi_json)
    return {"count": len(interfaces), "interfaces": [item.model_dump() for item in interfaces]}


@app.post("/api/ai/fill-params")
def ai_fill_params(request: FillParamsRequest) -> dict:
    result = fill_parameters(request.interface, request.project_root)
    return result.model_dump()


@app.post("/api/project/start")
def start_project(request: StartProjectRequest) -> dict:
    return project_runner.start(
        project_name=request.project_name,
        command=request.command,
        cwd=request.cwd,
        ready_keyword=request.ready_keyword,
        timeout_seconds=request.timeout_seconds,
    )


@app.post("/api/project/stop")
def stop_project(request: StopProjectRequest) -> dict:
    return project_runner.stop(request.project_name)


@app.get("/api/project/status/{project_name}")
def project_status(project_name: str) -> dict:
    return project_runner.status(project_name)


@app.post("/api/test/api")
def api_test(request: ApiTestRequest) -> dict:
    return run_api_test(
        base_url=str(request.base_url),
        path=request.path,
        method=request.method,
        query=request.query,
        headers=request.headers,
        body=request.body,
        timeout_seconds=request.timeout_seconds,
    )


@app.post("/api/test/ui")
def ui_test(request: UiTestRequest) -> dict:
    return run_ui_smoke_test(url=str(request.url), wait_ms=request.wait_ms)


@app.post("/api/workflow/after-interface")
def workflow_after_interface(request: WorkflowRequest) -> dict:
    start_result = None
    if request.start_project:
        start_result = project_runner.start(
            project_name=request.start_project.project_name,
            command=request.start_project.command,
            cwd=request.start_project.cwd,
            ready_keyword=request.start_project.ready_keyword,
            timeout_seconds=request.start_project.timeout_seconds,
        )

    filled = fill_parameters(request.interface, request.project_root)

    api_result = None
    if request.api_test_base_url:
        api_result = run_api_test(
            base_url=str(request.api_test_base_url),
            path=request.interface.path,
            method=request.interface.method,
            query=filled.query,
            headers=filled.headers,
            body=filled.body,
            timeout_seconds=20,
        )

    ui_result = None
    if request.run_ui_test_url:
        ui_result = run_ui_smoke_test(str(request.run_ui_test_url))

    return {
        "filled_params": filled.model_dump(),
        "start_project_result": start_result,
        "api_test_result": api_result,
        "ui_test_result": ui_result,
    }
