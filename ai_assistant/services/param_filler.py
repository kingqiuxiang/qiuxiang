from __future__ import annotations

import json
import os
from typing import Any

import httpx

from ai_assistant.models import AutofillResponse, EndpointSpec
from ai_assistant.services.code_context import build_context


def _guess_value(name: str) -> Any:
    key = name.lower()
    if "id" in key:
        return 1
    if "phone" in key or "mobile" in key:
        return "13800000000"
    if "email" in key:
        return "demo@example.com"
    if "name" in key:
        return "demo-name"
    if "token" in key:
        return "demo-token"
    if "time" in key or "date" in key:
        return "2026-01-01T00:00:00Z"
    if "enable" in key or "is_" in key or key.startswith("has"):
        return True
    return "demo-value"


def _fallback_fill(endpoint: EndpointSpec) -> AutofillResponse:
    query = {name: _guess_value(name) for name in endpoint.query_params}
    path = {name: _guess_value(name) for name in endpoint.path_params}
    body = {name: _guess_value(name) for name in endpoint.body_params}
    return AutofillResponse(
        query=query,
        path=path,
        body=body,
        reason="使用本地规则填充（未检测到可用 AI 配置）",
    )


def _call_llm(prompt: str) -> dict[str, Any]:
    base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 未配置")

    payload = {
        "model": model,
        "temperature": 0.1,
        "messages": [
            {"role": "system", "content": "你是接口参数生成助手。只返回 JSON。"},
            {"role": "user", "content": prompt},
        ],
    }
    headers = {"Authorization": f"Bearer {api_key}"}
    with httpx.Client(timeout=30.0) as client:
        resp = client.post(f"{base_url}/chat/completions", headers=headers, json=payload)
        resp.raise_for_status()
        data = resp.json()
    text = data["choices"][0]["message"]["content"]
    return json.loads(text)


def autofill_params(endpoint: EndpointSpec, project_root: str, extra_prompt: str = "") -> AutofillResponse:
    keywords = endpoint.query_params + endpoint.path_params + endpoint.body_params + [endpoint.path]
    context = build_context(project_root=project_root, keywords=keywords)

    prompt = (
        f"接口信息:\n{endpoint.model_dump_json(indent=2)}\n\n"
        f"项目上下文:\n{context}\n\n"
        f"额外要求:\n{extra_prompt}\n\n"
        "请输出 JSON: {\"query\":{},\"path\":{},\"body\":{},\"reason\":\"...\"}"
    )

    try:
        response = _call_llm(prompt)
        return AutofillResponse(
            query=response.get("query", {}),
            path=response.get("path", {}),
            body=response.get("body", {}),
            reason=str(response.get("reason", "AI 生成")),
        )
    except Exception:
        return _fallback_fill(endpoint)
