$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot/..
$env:PATH = "$env:HOME/.local/bin:$env:PATH"
uv run pytest tests src/forge_web/forge_web/tests -q
uv run mypy src/pyforge/domain src/pyforge/services
uv run python src/forge_web/manage.py check
uv run python src/forge_web/manage.py pyforge_doctor
uv run python src/forge_web/manage.py pyforge_plugins
uv run python -c "import pyforge; assert pyforge.__version__"
uv run pyforge --version
uv run pyforge --help
Write-Output "ci ok"
