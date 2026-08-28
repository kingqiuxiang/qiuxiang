#!/usr/bin/env bash
# Cloud / 本机幂等安装：装 uv + Python 3.12，有 pyforge/ 就 uv sync。
# 不激活 venv，不跑 pytest，不 uv init。
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

# 仓根是 qiuxiang（pyforge 子目录），或仓根就是 pyforge。
if [[ -d pyforge && -f pyforge/pyproject.toml ]]; then
  (cd pyforge && uv sync --frozen)
elif [[ -f pyproject.toml && -d src/pyforge ]]; then
  uv sync --frozen
fi
