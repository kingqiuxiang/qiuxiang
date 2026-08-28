import sys


def test_running_inside_project_venv():
    prefix = sys.prefix.replace("\\", "/").lower()
    exe = sys.executable.replace("\\", "/").lower()
    assert ".venv" in prefix or ".venv" in exe
