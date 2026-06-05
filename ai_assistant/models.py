from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class EndpointSpec(BaseModel):
    path: str
    method: str = Field(default="GET")
    query_params: list[str] = Field(default_factory=list)
    path_params: list[str] = Field(default_factory=list)
    body_params: list[str] = Field(default_factory=list)
    headers: dict[str, str] = Field(default_factory=dict)


class YapiImportRequest(BaseModel):
    source: str
    is_url: bool = False


class YapiImportResponse(BaseModel):
    endpoints: list[EndpointSpec]


class AutofillRequest(BaseModel):
    endpoint: EndpointSpec
    project_root: str = "."
    extra_prompt: str = ""


class AutofillResponse(BaseModel):
    query: dict[str, Any]
    path: dict[str, Any]
    body: dict[str, Any]
    reason: str


class QuickStartRequest(BaseModel):
    command: str
    cwd: str = "."


class QuickStartResponse(BaseModel):
    pid: int
    command: str
    cwd: str


class ApiTestRequest(BaseModel):
    base_url: str
    endpoint: EndpointSpec
    query: dict[str, Any] = Field(default_factory=dict)
    path: dict[str, Any] = Field(default_factory=dict)
    body: dict[str, Any] = Field(default_factory=dict)
    frontend_url: str | None = None


class ApiTestResponse(BaseModel):
    request_url: str
    status_code: int
    response_body: Any
    frontend_status: int | None = None
    frontend_title: str | None = None
