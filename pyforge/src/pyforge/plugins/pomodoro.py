from __future__ import annotations

from pyforge.session import DailySession


class PomodoroPlugin:
    name = "pomodoro"

    def on_session_stop(self, session: DailySession) -> None:
        if "pomodoro" not in session.tags:
            session.tags.append("pomodoro")


def load() -> PomodoroPlugin:
    return PomodoroPlugin()
