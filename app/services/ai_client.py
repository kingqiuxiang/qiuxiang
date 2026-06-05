from __future__ import annotations

import json
import os
from typing import Any

import httpx


class AIClient:
    def __init__(self) -> None:
        self.api_key = os.getenv("OPENAI_API_KEY", "")
        self.base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
        self.model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    @property
    def enabled(self) -> bool:
        return bool(self.api_key)

    def generate_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any] | None:
        if not self.enabled:
            return None

        url = f"{self.base_url.rstrip('/')}/chat/completions"
        headers = {"Authorization": f"Bearer {self.api_key}"}
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.1,
            "response_format": {"type": "json_object"},
        }
        try:
            response = httpx.post(url, headers=headers, json=payload, timeout=35)
            response.raise_for_status()
            message = response.json()["choices"][0]["message"]["content"]
            return json.loads(message)
        except (httpx.HTTPError, KeyError, json.JSONDecodeError, ValueError):
            return None
