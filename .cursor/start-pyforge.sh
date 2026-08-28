#!/usr/bin/env bash
# 每场 Agent 开机：PATH 带上 uv；已 checkout 到带 pyforge 的分支就 sync。
# 必须退出。不要起 Django / frontend。
set -euo pipefail

export PATH="${HOME}/.local/bin:${PATH}"

if [[ -d /workspace/pyforge && -f /workspace/pyforge/pyproject.toml ]]; then
  cd /workspace/pyforge
  uv sync --frozen
elif [[ -f /workspace/pyproject.toml && -d /workspace/src/pyforge ]]; then
  cd /workspace
  uv sync --frozen
fi
