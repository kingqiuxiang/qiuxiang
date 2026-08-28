$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot/..
$env:PATH = "$env:HOME/.local/bin:$env:PATH"
uv run pytest tests/test_session.py tests/test_copying.py tests/test_slices.py tests/test_gate.py -q
uv run pytest tests/test_cli.py tests/test_sqlite_repo.py tests/test_export.py -q
uv run mypy src/pyforge/domain src/pyforge/services
uv run pytest tests/test_course_manifest.py tests/test_repo_protocol.py -q
uv run pytest tests/test_gil_bench.py tests/test_gate_runner.py tests/test_catalog_fetch.py -q
uv run pytest tests/test_import_events.py tests/test_weekly_report.py tests/test_gate_attempts.py -q
uv run python src/forge_web/manage.py check
uv run python src/forge_web/manage.py test forge_web.tests.test_session_form forge_web.tests.test_week_list forge_web.tests.test_api
uv run pytest tests/test_plugin_isolation.py tests/test_plugin_discover.py tests/test_observation_event.py -q
uv run pytest tests/test_log_context.py tests/test_error_redact.py -q
uv run python src/forge_web/manage.py test forge_web.tests.test_metrics
uv run python -c "import pyforge; assert pyforge.__version__"
uv run pyforge --version
Write-Output "ci ok"
