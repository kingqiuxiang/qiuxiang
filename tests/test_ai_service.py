import pytest

from app.models import AIFillRequest, EndpointSummary
from app.services.ai_service import fill_params_with_ai


@pytest.mark.asyncio
async def test_fallback_fill_params_without_api_key(monkeypatch):
    monkeypatch.delenv("AI_API_KEY", raising=False)
    request = AIFillRequest(
        endpoint=EndpointSummary(
            title="Create User",
            method="POST",
            path="/api/user/create",
            request_schema={
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer"},
                    "enabled": {"type": "boolean"},
                },
            },
        )
    )

    params, reasoning, provider = await fill_params_with_ai(request)
    assert params["name"] == "example-text"
    assert params["age"] == 1
    assert params["enabled"] is True
    assert provider == "fallback"
    assert "fallback" in reasoning.lower()
