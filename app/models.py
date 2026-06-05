from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class YapiLoadRequest(BaseModel):
    yapi_content: dict[str, Any]


class EndpointSummary(BaseModel):
    title: str
    method: str
    path: str
    description: str | None = None
    request_schema: dict[str, Any] = Field(default_factory=dict)
    response_schema: dict[str, Any] = Field(default_factory=dict)


class YapiLoadResponse(BaseModel):
    endpoints: list[EndpointSummary]


class CodeScanRequest(BaseModel):
    project_path: str = "."
    keywords: list[str] = Field(default_factory=list)
    max_files: int = 10


class CodeScanResult(BaseModel):
    file_path: str
    snippet: str


class CodeScanResponse(BaseModel):
    results: list[CodeScanResult]


class AIFillRequest(BaseModel):
    endpoint: EndpointSummary
    code_context: list[CodeScanResult] = Field(default_factory=list)
    preferred_locale: Literal["zh-CN", "en-US"] = "zh-CN"


class AIFillResponse(BaseModel):
    generated_params: dict[str, Any]
    reasoning: str
    provider: str


class AssertionRule(BaseModel):
    path: str
    equals: Any


class APITestRequest(BaseModel):
    base_url: str
    method: str
    path: str
    headers: dict[str, str] = Field(default_factory=dict)
    params: dict[str, Any] = Field(default_factory=dict)
    body: dict[str, Any] = Field(default_factory=dict)
    assertions: list[AssertionRule] = Field(default_factory=list)


class APITestResponse(BaseModel):
    status_code: int
    ok: bool
    elapsed_ms: int
    response_body: Any
    assertion_results: list[str]


class ProjectStartRequest(BaseModel):
    command: str
    working_directory: str = "."
    env: dict[str, str] = Field(default_factory=dict)


class ProjectStartResponse(BaseModel):
    return_code: int
    stdout: str
    stderr: str


class FrontendCheck(BaseModel):
    selector: str
    contains_text: str | None = None


class FrontendTestRequest(BaseModel):
    url: str
    checks: list[FrontendCheck] = Field(default_factory=list)
    timeout_ms: int = 5000


class FrontendTestResponse(BaseModel):
    passed: bool
    details: list[str]
