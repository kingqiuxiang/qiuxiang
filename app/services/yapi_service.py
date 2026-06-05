from __future__ import annotations

from typing import Any

from app.models import EndpointSummary


def _normalize_method(method: str | None) -> str:
    if not method:
        return "GET"
    return method.upper()


def _extract_endpoints_from_item(item: dict[str, Any]) -> list[EndpointSummary]:
    endpoints: list[EndpointSummary] = []

    for interface in item.get("list", []):
        endpoints.append(
            EndpointSummary(
                title=interface.get("title", "Untitled API"),
                method=_normalize_method(interface.get("method")),
                path=interface.get("path", "/"),
                description=interface.get("desc") or interface.get("markdown"),
                request_schema=interface.get("req_body_other")
                if isinstance(interface.get("req_body_other"), dict)
                else {},
                response_schema=interface.get("res_body")
                if isinstance(interface.get("res_body"), dict)
                else {},
            )
        )
    return endpoints


def parse_yapi_content(yapi_content: dict[str, Any]) -> list[EndpointSummary]:
    """
    Support common YAPI exports:
    1) Top-level list of categories: {"list":[{"list":[...interfaces]}]}
    2) Single interface export object with method/path/title
    """
    if "list" in yapi_content and isinstance(yapi_content["list"], list):
        endpoints: list[EndpointSummary] = []
        for item in yapi_content["list"]:
            if isinstance(item, dict) and "list" in item:
                endpoints.extend(_extract_endpoints_from_item(item))
            elif isinstance(item, dict) and "path" in item:
                endpoints.append(
                    EndpointSummary(
                        title=item.get("title", "Untitled API"),
                        method=_normalize_method(item.get("method")),
                        path=item.get("path", "/"),
                        description=item.get("desc") or item.get("markdown"),
                        request_schema=item.get("req_body_other")
                        if isinstance(item.get("req_body_other"), dict)
                        else {},
                        response_schema=item.get("res_body")
                        if isinstance(item.get("res_body"), dict)
                        else {},
                    )
                )
        return endpoints

    if "path" in yapi_content:
        return [
            EndpointSummary(
                title=yapi_content.get("title", "Untitled API"),
                method=_normalize_method(yapi_content.get("method")),
                path=yapi_content.get("path", "/"),
                description=yapi_content.get("desc") or yapi_content.get("markdown"),
                request_schema=yapi_content.get("req_body_other")
                if isinstance(yapi_content.get("req_body_other"), dict)
                else {},
                response_schema=yapi_content.get("res_body")
                if isinstance(yapi_content.get("res_body"), dict)
                else {},
            )
        ]

    return []
