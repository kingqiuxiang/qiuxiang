from __future__ import annotations

import json
from typing import Any

from app.schemas import InterfaceDefinition, InterfaceField


def _to_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value == 1
    if isinstance(value, str):
        return value.lower() in {"1", "true", "yes", "required"}
    return False


def _build_field(item: dict[str, Any], fallback_type: str = "string") -> InterfaceField:
    field_type = str(item.get("type") or fallback_type).lower()
    return InterfaceField(
        name=item.get("name", ""),
        required=_to_bool(item.get("required")),
        desc=item.get("desc", "") or item.get("description", ""),
        example=item.get("example"),
        field_type=field_type,
    )


def _parse_body_schema(raw_schema: Any) -> dict[str, Any] | None:
    if raw_schema is None:
        return None
    if isinstance(raw_schema, dict):
        return raw_schema
    if isinstance(raw_schema, str) and raw_schema.strip():
        try:
            return json.loads(raw_schema)
        except json.JSONDecodeError:
            return None
    return None


def _extract_interface_list(yapi_payload: dict[str, Any]) -> list[dict[str, Any]]:
    if "list" in yapi_payload and isinstance(yapi_payload["list"], list):
        return yapi_payload["list"]

    data = yapi_payload.get("data")
    if isinstance(data, list):
        # YAPI project export sometimes nests interfaces in category.list
        collected: list[dict[str, Any]] = []
        for item in data:
            if isinstance(item, dict) and isinstance(item.get("list"), list):
                collected.extend(item["list"])
        if collected:
            return collected

    if isinstance(data, dict) and isinstance(data.get("list"), list):
        return data["list"]
    return []


def parse_yapi_interfaces(yapi_payload: dict[str, Any]) -> list[InterfaceDefinition]:
    interfaces: list[InterfaceDefinition] = []
    for item in _extract_interface_list(yapi_payload):
        method = str(item.get("method", "GET")).upper()
        interface = InterfaceDefinition(
            interface_id=item.get("_id") or item.get("id"),
            title=item.get("title", "Untitled API"),
            path=item.get("path", "/"),
            method=method,
            description=item.get("desc", "") or item.get("markdown", ""),
            query_fields=[_build_field(field) for field in item.get("req_query", [])],
            path_fields=[_build_field(field) for field in item.get("req_params", [])],
            header_fields=[_build_field(field) for field in item.get("req_headers", [])],
            body_form_fields=[_build_field(field) for field in item.get("req_body_form", [])],
            body_schema=_parse_body_schema(item.get("req_body_other")),
        )
        interfaces.append(interface)
    return interfaces
