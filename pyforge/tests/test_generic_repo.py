from pyforge.domain.generic import MemoryRepository, UnitOfWork
from pyforge.session import DailySession
from pyforge.slices import KnowledgeSlice


def test_same_generic_shell() -> None:
    sessions: MemoryRepository[DailySession] = MemoryRepository()
    slices: MemoryRepository[KnowledgeSlice] = MemoryRepository()
    sessions.add(DailySession("W10"))
    slices.add(KnowledgeSlice("W10", "g", ("uv",)))
    uow: UnitOfWork[DailySession] = UnitOfWork(sessions)
    uow.commit()
    assert uow.committed is True
    assert len(sessions.list_all()) == 1
    assert len(slices.list_all()) == 1
