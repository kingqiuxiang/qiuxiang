$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot/..
$env:PATH = "$env:HOME/.local/bin:$env:PATH"
if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
  throw "uv is required"
}
uv sync --frozen
uv run python src/forge_web/manage.py migrate --noinput
uv run python src/forge_web/manage.py seed_demo
pwsh -File scripts/ci.ps1
Write-Output "bootstrap ok"
