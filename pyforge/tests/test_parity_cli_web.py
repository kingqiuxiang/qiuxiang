from pathlib import Path

import pytest

from pyforge.cli import main
from pyforge.session_store import load_sessions


@pytest.mark.django_db
def test_cli_session_fields_show_up_in_web(tmp_path: Path, client) -> None:
    store = tmp_path / "sessions.json"
    assert main(["session", "start", "W27"], store_path=store) == 0
    session = load_sessions(store)[0]
    from forge_web.models import Session

    Session.objects.create(slice_id=session.slice_id, tags=list(session.tags))
    resp = client.get("/weeks/")
    assert resp.status_code == 200
    assert session.slice_id in resp.content.decode()
