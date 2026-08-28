from pyforge.domain.modules import load_manifest, load_module_book, module_for_week


def test_eight_modules_cover_forty_eight_weeks() -> None:
    book = load_module_book()
    manifest = load_manifest()
    assert len(book.modules) == 8
    covered = [n for module in book.modules for n in module.weeks]
    assert covered == [week.n for week in manifest.weeks]
    assert module_for_week(book, 1).id == "foundation"
    assert module_for_week(book, 23).id == "web"
    assert module_for_week(book, 13).title == "底层"
    assert "docs.djangoproject.com" in " ".join(src.url for src in module_for_week(book, 21).sources)
    assert "flowchart" in book.progression
