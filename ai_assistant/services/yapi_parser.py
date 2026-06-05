from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import httpx

from ai_assistant.models import EndpointSpec


def _to_list(value: Any) -> list[Any]:
    if isinstance(value, list):
        return value
    return []


def _extract_name(item: Any) -> str | None:
    if not isinstance(item, dict):
        return None
    name = item.get("name") or item.get("key")
    if isinstance(name, str) and name.strip():
        return name.strip()
    return None


def _endpoint_from_yapi_item(item: dict[str, Any]) -> EndpointSpec | None:
    path = item.get("path")
    method = item.get("method", "GET")
    if not isinstance(path, str) or not path.strip():
        return None

    query_params = [_extract_name(i) for i in _to_list(item.get("req_query"))]
    path_params = [_extract_name(i) for i in _to_list(item.get("req_params"))]
    body_params = [_extract_name(i) for i in _to_list(item.get("req_body_form"))]

    body_other = item.get("req_body_other")
    if isinstance(body_other, str):
        try:
            body_schema = json.loads(body_other)
            if isinstance(body_schema, dict):
                for key in body_schema.keys():
                    if isinstance(key, str):
                        body_params.append(key)
        except json.JSONDecodeError:
            pass

    return EndpointSpec(
        path=path.strip(),
        method=str(method).upper(),
        query_params=[i for i in query_params if i],
        path_params=[i for i in path_params if i],
        body_params=[i for i in body_params if i],
    )


def parse_yapi_payload(payload: dict[str, Any]) -> list[EndpointSpec]:
    # YAPI export formats are inconsistent across versions.
    roots = [payload]
    data = payload.get("data")
    if isinstance(data, dict):
        roots.append(data)

    endpoints: list[EndpointSpec] = []
    for root in roots:
        for key in ("list", "items", "apis"):
            items = root.get(key)
            if not isinstance(items, list):
                continue
            for item in items:
                if not isinstance(item, dict):
                    continue
                parsed = _endpoint_from_yapi_item(item)
                if parsed is not None:
                    endpoints.append(parsed)
    return endpoints


def load_yapi_source(source: str, is_url: bool) -> list[EndpointSpec]:
    if is_url:
        with httpx.Client(timeout=20.0) as client:
            resp = client.get(source)
            resp.raise_for_status()
            data = resp.json()
            if not isinstance(data, dict):
                raise ValueError("YAPI 返回不是 JSON 对象")
            return parse_yapi_payload(data)

    content = Path(source).read_text(encoding="utf-8")
    payload = json.loads(content)
    if not isinstance(payload, dict):
        raise ValueError("YAPI 文件内容不是 JSON 对象")
    return parse_yapi_payload(payload)
