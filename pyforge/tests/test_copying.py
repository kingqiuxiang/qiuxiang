from pyforge.copying import deep_rows, shallow_rows


def test_slice_is_shallow():
    a = [["gil"], ["uv"]]
    b = shallow_rows(a)
    b[0][0] = "changed"
    assert a[0][0] == "changed"


def test_deepcopy_isolates():
    a = [["gil"], ["uv"]]
    b = deep_rows(a)
    b[0][0] = "changed"
    assert a[0][0] == "gil"
