from __future__ import annotations

import json
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.models import (
    AIFillRequest,
    AIFillResponse,
    APITestRequest,
    APITestResponse,
    CodeScanRequest,
    CodeScanResponse,
    FrontendTestRequest,
    FrontendTestResponse,
    ProjectStartRequest,
    ProjectStartResponse,
    YapiLoadRequest,
    YapiLoadResponse,
)
from app.services.ai_service import fill_params_with_ai
from app.services.code_service import scan_code_context
from app.services.runtime_service import run_frontend_test, start_project
from app.services.test_service import run_api_test
from app.services.yapi_service import parse_yapi_content

app = FastAPI(title="AI YAPI AutoTest System", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

static_dir = Path(__file__).parent / "static"
app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")


@app.get("/")
def index() -> FileResponse:
    return FileResponse(static_dir / "index.html")


@app.post("/api/yapi/load", response_model=YapiLoadResponse)
def load_yapi_from_json(request: YapiLoadRequest) -> YapiLoadResponse:
    endpoints = parse_yapi_content(request.yapi_content)
    return YapiLoadResponse(endpoints=endpoints)


@app.post("/api/yapi/upload", response_model=YapiLoadResponse)
async def upload_yapi_json(file: UploadFile = File(...)) -> YapiLoadResponse:
    if not file.filename.lower().endswith(".json"):
        raise HTTPException(status_code=400, detail="Only JSON files are supported.")

    content = await file.read()
    try:
        yapi_data = json.loads(content.decode("utf-8"))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid JSON: {exc}") from exc

    endpoints = parse_yapi_content(yapi_data)
    return YapiLoadResponse(endpoints=endpoints)


@app.post("/api/code/scan", response_model=CodeScanResponse)
def code_scan(request: CodeScanRequest) -> CodeScanResponse:
    results = scan_code_context(
        project_path=request.project_path,
        keywords=request.keywords,
        max_files=request.max_files,
    )
    return CodeScanResponse(results=results)


@app.post("/api/ai/fill-params", response_model=AIFillResponse)
async def ai_fill_params(request: AIFillRequest) -> AIFillResponse:
    params, reasoning, provider = await fill_params_with_ai(request)
    return AIFillResponse(generated_params=params, reasoning=reasoning, provider=provider)


@app.post("/api/test/api", response_model=APITestResponse)
async def test_api(request: APITestRequest) -> APITestResponse:
    return await run_api_test(request)


@app.post("/api/project/start", response_model=ProjectStartResponse)
def project_start(request: ProjectStartRequest) -> ProjectStartResponse:
    return start_project(request)


@app.post("/api/test/frontend", response_model=FrontendTestResponse)
async def test_frontend(request: FrontendTestRequest) -> FrontendTestResponse:
    return await run_frontend_test(request)
