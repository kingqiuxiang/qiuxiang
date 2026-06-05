from __future__ import annotations

import json
import os
from typing import Any

import httpx

from app.models import AIFillRequest


def _example_value_from_schema(schema: dict[str, Any]) -> Any:
    if not isinstance(schema, dict):
        return "example"

    schema_type = schema.get("type")
    if schema_type == "string":
        return schema.get("example") or "example-text"
    if schema_type in {"integer", "number"}:
        return schema.get("example") or 1
    if schema_type == "boolean":
        return schema.get("example") if "example" in schema else True
    if schema_type == "array":
        item_schema = schema.get("items", {})
        return [_example_value_from_schema(item_schema)]
    if schema_type == "object":
        props = schema.get("properties", {})
        if isinstance(props, dict):
            return {k: _example_value_from_schema(v) for k, v in props.items()}
    return schema.get("example") or "example"


def _build_fallback_params(request: AIFillRequest) -> dict[str, Any]:
    schema = request.endpoint.request_schema
    if not isinstance(schema, dict) or not schema:
        return {"id": 1}

    if "properties" in schema and isinstance(schema["properties"], dict):
        return {k: _example_value_from_schema(v) for k, v in schema["properties"].items()}

    return _example_value_from_schema(schema)


async def _call_openai_compatible(request: AIFillRequest) -> tuple[dict[str, Any], str]:
    base_url = os.getenv("AI_BASE_URL", "https://api.openai.com/v1")
    api_key = os.getenv("AI_API_KEY")
    model = os.getenv("AI_MODEL", "gpt-4o-mini")
    if not api_key:
        raise RuntimeError("AI_API_KEY is not set")

    endpoint_json = request.endpoint.model_dump()
    code_context_json = [ctx.model_dump() for ctx in request.code_context]

    prompt = (
        "You are an API test data generation assistant.\n"
        "Generate realistic JSON request parameters for the endpoint.\n"
        "Return ONLY valid JSON object without markdown fences.\n"
        f"Endpoint: {json.dumps(endpoint_json, ensure_ascii=False)}\n"
        f"CodeContext: {json.dumps(code_context_json, ensure_ascii=False)}\n"
    )

    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.post(
            f"{base_url.rstrip('/')}/chat/completions",
            headers={"Authorization": f"Bearer {api_key}"},
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": "Return JSON parameters only."},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.2,
            },
        )
        response.raise_for_status()
        payload = response.json()

    text = payload["choices"][0]["message"]["content"].strip()
    generated = json.loads(text)
    if not isinstance(generated, dict):
        raise ValueError("Model output is not a JSON object")
    return generated, model


async def fill_params_with_ai(request: AIFillRequest) -> tuple[dict[str, Any], str, str]:
    try:
        generated, provider = await _call_openai_compatible(request)
        return generated, "AI generated from endpoint schema and code context.", provider
    except Exception:
        fallback = _build_fallback_params(request)
        return (
            fallback if isinstance(fallback, dict) else {"payload": fallback},
            "Used local schema-based fallback because AI provider is unavailable.",
            "fallback",
        )
