import subprocess
import sys


def test_python_m_pyforge_version() -> None:
    proc = subprocess.run(
        [sys.executable, "-m", "pyforge", "--version"],
        check=False,
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0
    assert "0.1.0" in proc.stdout
