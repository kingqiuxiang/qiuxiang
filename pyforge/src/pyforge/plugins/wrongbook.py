from __future__ import annotations

from pyforge.session import DailySession


class WrongbookPlugin:
    name = "wrongbook"

    def on_session_stop(self, session: DailySession) -> None:
        if "wrongbook" not in session.tags:
            session.tags.append("wrongbook")


def load() -> WrongbookPlugin:
    return WrongbookPlugin()
