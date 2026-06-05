from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, HttpUrl


class YapiImportRequest(BaseModel):
    source_type: Literal["json", "url"] = "json"
    yapi_json: dict[str, Any] | None = None
    yapi_url: HttpUrl | None = None


class InterfaceField(BaseModel):
    name: str
    required: bool = False
    desc: str = ""
    example: Any | None = None
    field_type: str = "string"


class InterfaceDefinition(BaseModel):
    interface_id: int | None = None
    title: str
    path: str
    method: str
    description: str = ""
    query_fields: list[InterfaceField] = Field(default_factory=list)
    path_fields: list[InterfaceField] = Field(default_factory=list)
    header_fields: list[InterfaceField] = Field(default_factory=list)
    body_form_fields: list[InterfaceField] = Field(default_factory=list)
    body_schema: dict[str, Any] | None = None


class FillParamsRequest(BaseModel):
    interface: InterfaceDefinition
    project_root: str = "."


class FillParamsResponse(BaseModel):
    source: Literal["ai", "heuristic"]
    query: dict[str, Any]
    path_params: dict[str, Any]
    headers: dict[str, Any]
    body: dict[str, Any]
    reasoning: str


class StartProjectRequest(BaseModel):
    project_name: str
    command: str
    cwd: str = "."
    ready_keyword: str | None = None
    timeout_seconds: int = 25


class StopProjectRequest(BaseModel):
    project_name: str


class ApiTestRequest(BaseModel):
    base_url: HttpUrl
    path: str
    method: str = "GET"
    query: dict[str, Any] = Field(default_factory=dict)
    headers: dict[str, Any] = Field(default_factory=dict)
    body: dict[str, Any] = Field(default_factory=dict)
    timeout_seconds: int = 20


class UiTestRequest(BaseModel):
    url: HttpUrl
    wait_ms: int = 3500


class WorkflowRequest(BaseModel):
    interface: InterfaceDefinition
    project_root: str = "."
    start_project: StartProjectRequest | None = None
    api_test_base_url: HttpUrl | None = None
    run_ui_test_url: HttpUrl | None = None
