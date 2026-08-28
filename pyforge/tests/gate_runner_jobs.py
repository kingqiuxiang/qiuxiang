def ok_job() -> str:
    return "ok"


def slow_job() -> str:
    import time

    time.sleep(8)
    return "late"
