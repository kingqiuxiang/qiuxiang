$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot/..
$env:PATH = "$env:HOME/.local/bin:$env:PATH"

pwsh -File scripts/ci.ps1
uv run pytest --cov=src/pyforge --cov-fail-under=80 -q
uv run python src/forge_web/manage.py import_course courses/curriculum.yaml
uv run python src/forge_web/manage.py seed_demo
$backup = Join-Path ([System.IO.Path]::GetTempPath()) "pyforge-backup.json"
uv run python src/forge_web/manage.py pyforge_backup --out $backup
uv run python -m pyforge --version

$prev = $ErrorActionPreference
$ErrorActionPreference = "Continue"
uv run python src/forge_web/manage.py pyforge_doctor --env prod
$code = $LASTEXITCODE
$ErrorActionPreference = $prev
if ($code -eq 0) {
  throw "pyforge_doctor --env prod must fail when DEBUG=True"
}

Write-Output "verify_all ok"
