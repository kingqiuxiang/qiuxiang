from pyforge.services.gil_bench import wall_single, wall_two_threads


def test_two_threads_are_not_twice_as_fast() -> None:
    n = 250_000
    single = wall_single(n)
    double = wall_two_threads(n)
    assert single > 0
    assert double > single * 0.7
