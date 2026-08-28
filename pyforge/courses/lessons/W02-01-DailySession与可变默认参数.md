# W02-01 DailySession 与可变默认参数

## 今晚能感觉到什么

你 `start()` 两次，往第一条的 `tags` 里 `append("gil")`，第二条的 `tags` 仍是空的。如果写成 Java 直觉的默认参数，两条会**共享同一个 list**——今晚就是要看见这个坑，再修好。

## 和 Java 不同的一句

Java：默认参数没有这个坑；每次调用的 `new ArrayList<>()` 都是新的。  
Python：`def f(tags=[])` 里的 `[]` **只在定义时创建一次**，所有调用共用。可变默认参数是语言级地雷。

## 示范（先原样跑）

`src/pyforge/session.py`

```python
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone


@dataclass
class DailySession:
    slice_id: str
    started_at: datetime | None = None  # | 是 3.10 联合类型，当 Java 的 T|null
    ended_at: datetime | None = None
    tags: list[str] = field(default_factory=list)

    def start(self) -> DailySession:
        if self.started_at is not None:
            raise RuntimeError("already started")
        self.started_at = datetime.now(timezone.utc)
        return self

    def stop(self) -> DailySession:
        if self.started_at is None:
            raise RuntimeError("not started")
        self.ended_at = datetime.now(timezone.utc)
        return self
```

先写**会红**的对比测试，再保证实现用 `default_factory`：

`tests/test_session.py`

```python
from pyforge.session import DailySession


def test_two_sessions_do_not_share_tags():
    a = DailySession("W02").start()
    b = DailySession("W02").start()
    a.tags.append("gil")
    assert b.tags == []
    assert a.tags == ["gil"]
```

```powershell
uv run pytest tests/test_session.py -q
```

用手摸：

```powershell
uv run python -c "from pyforge.session import DailySession; a=DailySession('W02').start(); b=DailySession('W02').start(); a.tags.append('gil'); print(a.tags, b.tags)"
```

要看到：`['gil'] []`。

## 反例（必错）

```python
def broken_start(tags=[]):
    tags.append("x")
    return tags

print("第一次", broken_start())
print("第二次", broken_start())
```

两次都是 `['x', 'x']` 也正常：它们是**同一个 list**，`print` 看见的是最后的样子。拆开写也一样，第二次不是从空开始。  
所以 `tags` 必须 `field(default_factory=list)`，禁止 `tags=[]`。

## 今晚只改这一刀

- `src/pyforge/session.py`
- `tests/test_session.py`

禁止：写盘、sqlite、CLI、第二个 dataclass。

## 验收命令

```powershell
uv run pytest tests/test_session.py -q
```

外加上面那行 `print(a.tags, b.tags)` 你亲眼看见。

## 下一课怎么接

`DailySession` 留下。下一课给它加「空 tags 不能当有课上」的判断——Python 里空列表是假。
