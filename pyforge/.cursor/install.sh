#!/usr/bin/env bash
# Cursor Cloud Build。必须幂等。不要激活 venv。
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
uv run pytest tests/test_version.py -q
uv run python -c "import pyforge; print(pyforge.__version__)"
