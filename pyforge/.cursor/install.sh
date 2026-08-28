#!/usr/bin/env bash
# 当仓根就是 pyforge 时用。必须幂等。不要激活 venv。不要在 install 里跑测试。
set -euo pipefail

export PATH="${HOME}/.local/bin:${PATH}"

if ! command -v uv >/dev/null 2>&1; then
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="${HOME}/.local/bin:${PATH}"
fi

if ! grep -q '.local/bin' "${HOME}/.profile" 2>/dev/null; then
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "${HOME}/.profile"
fi

uv python install 3.12
uv sync --frozen
