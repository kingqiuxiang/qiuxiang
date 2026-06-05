from __future__ import annotations

import re
from typing import Any

from app.schemas import FillParamsResponse, InterfaceDefinition, InterfaceField
from app.services.ai_client import AIClient
from app.services.code_context import collect_project_context


def _guess_by_name(name: str, field_type: str = "string") -> Any:
    lower_name = name.lower()
    if "id" in lower_name:
        return 1
    if lower_name in {"page", "pageno", "page_no"}:
        return 1
    if lower_name in {"size", "pagesize", "page_size", "limit"}:
        return 20
    if "phone" in lower_name or "mobile" in lower_name:
        return "13800138000"
    if "email" in lower_name:
        return "demo@example.com"
    if "name" in lower_name:
        return "demo-name"
    if "status" in lower_name:
        return 1
    if field_type in {"integer", "number", "int"}:
        return 1
    if field_type in {"boolean", "bool"}:
        return True
    if field_type in {"array"}:
        return []
    return "demo-value"


def _fill_fields(fields: list[InterfaceField]) -> dict[str, Any]:
    values: dict[str, Any] = {}
    for field in fields:
        if field.example not in (None, ""):
            values[field.name] = field.example
        else:
            values[field.name] = _guess_by_name(field.name, field.field_type)
    return values


def _resolve_schema_type(schema: dict[str, Any]) -> str:
    return str(schema.get("type", "object")).lower()


def _fill_schema(schema: dict[str, Any]) -> Any:
    schema_type = _resolve_schema_type(schema)
    if schema_type == "object":
        properties = schema.get("properties", {})
        if not isinstance(properties, dict):
            return {}
        result: dict[str, Any] = {}
        for prop_name, prop_schema in properties.items():
            if isinstance(prop_schema, dict):
                result[prop_name] = _fill_schema(prop_schema)
            else:
                result[prop_name] = "demo-value"
        return result

    if schema_type == "array":
        items = schema.get("items", {})
        if isinstance(items, dict):
            return [_fill_schema(items)]
        return []

    if schema_type in {"integer", "number"}:
        return 1
    if schema_type == "boolean":
        return True
    return "demo-value"


def _build_keywords(interface: InterfaceDefinition) -> list[str]:
    segments = [seg for seg in re.split(r"[/{}_-]+", interface.path) if seg]
    return [interface.title, interface.path, *segments]


def fill_parameters(interface: InterfaceDefinition, project_root: str) -> FillParamsResponse:
    heuristic_query = _fill_fields(interface.query_fields)
    heuristic_path = _fill_fields(interface.path_fields)
    heuristic_headers = _fill_fields(interface.header_fields)
    heuristic_body = _fill_schema(interface.body_schema) if interface.body_schema else _fill_fields(interface.body_form_fields)

    ai_client = AIClient()
    if not ai_client.enabled:
        return FillParamsResponse(
            source="heuristic",
            query=heuristic_query,
            path_params=heuristic_path,
            headers=heuristic_headers,
            body=heuristic_body if isinstance(heuristic_body, dict) else {"payload": heuristic_body},
            reasoning="OPENAI_API_KEY 未配置，已降级为规则填充。",
        )

    context = collect_project_context(project_root, _build_keywords(interface))
    system_prompt = (
        "你是接口联调助手。请基于接口定义和代码上下文，输出可直接请求接口的 JSON 参数。"
        "只返回 JSON 对象，结构必须包含 query/path_params/headers/body/reasoning。"
    )
    user_prompt = (
        f"接口标题: {interface.title}\n"
        f"接口路径: {interface.path}\n"
        f"请求方法: {interface.method}\n"
        f"query 字段: {interface.query_fields}\n"
        f"path 字段: {interface.path_fields}\n"
        f"header 字段: {interface.header_fields}\n"
        f"form 字段: {interface.body_form_fields}\n"
        f"body schema: {interface.body_schema}\n\n"
        f"项目代码上下文:\n{context}\n"
    )
    result = ai_client.generate_json(system_prompt, user_prompt)
    if not result:
        return FillParamsResponse(
            source="heuristic",
            query=heuristic_query,
            path_params=heuristic_path,
            headers=heuristic_headers,
            body=heuristic_body if isinstance(heuristic_body, dict) else {"payload": heuristic_body},
            reasoning="AI 调用失败，已降级为规则填充。",
        )

    return FillParamsResponse(
        source="ai",
        query=result.get("query", heuristic_query),
        path_params=result.get("path_params", heuristic_path),
        headers=result.get("headers", heuristic_headers),
        body=result.get("body", heuristic_body if isinstance(heuristic_body, dict) else {"payload": heuristic_body}),
        reasoning=result.get("reasoning", "由 AI 结合项目上下文生成。"),
    )
