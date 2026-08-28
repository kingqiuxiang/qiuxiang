from __future__ import annotations

from typing import Any

SECRET_KEYS = {"password", "token", "secret", "authorization"}


def redact_event(payload: dict[str, Any]) -> dict[str, Any]:
    redacted: dict[str, Any] = {}
    for key, value in payload.items():
        if key.lower() in SECRET_KEYS:
            redacted[key] = "***"
        elif isinstance(value, dict):
            redacted[key] = redact_event(value)
        else:
            redacted[key] = value
    return redacted
