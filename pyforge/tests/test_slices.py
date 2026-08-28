from pyforge.slices import KnowledgeSlice, SliceRegistry


def test_filter_does_not_leak_registry():
    reg = SliceRegistry()
    reg.add(KnowledgeSlice("W03-01", "copy", ("gil",)))
    reg.add(KnowledgeSlice("W03-02", "reg", ("uv",)))
    found = reg.by_tag("gil")
    assert [s.slice_id for s in found] == ["W03-01"]
    found.append(KnowledgeSlice("hack", "x", ("gil",)))
    assert [s.slice_id for s in reg.by_tag("gil")] == ["W03-01"]
