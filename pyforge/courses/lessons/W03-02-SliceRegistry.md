# W03-02 SliceRegistry：同一份 tags 换一种用法

## 今晚能感觉到什么

你有两节课切片，打了不同 tag。过滤 `gil` 只剩一节。过滤结果是新 list：往结果里 append 不会污染注册表。这是上一课浅拷贝的**有用版本**。

## 和 Java 不同的一句

Java：`stream().filter()` 每次新列表。  
Python：自己写过滤时，`result = src` 会把注册表漏出去；`result = src[:]` 或推导式才是新列表。推导式有独立作用域，别当 Java 的 for 块。

## 示范（先原样跑）

`src/pyforge/slices.py`

```python
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class KnowledgeSlice:
    slice_id: str
    title: str
    tags: tuple[str, ...]


class SliceRegistry:
    def __init__(self) -> None:
        self._items: list[KnowledgeSlice] = []

    def add(self, item: KnowledgeSlice) -> None:
        self._items.append(item)

    def by_tag(self, tag: str) -> list[KnowledgeSlice]:
        return [s for s in self._items if tag in s.tags]
```

`tests/test_slices.py`（浅拷贝在 `test_copying.py`，本课新建这个文件）：

```python
from pyforge.slices import KnowledgeSlice, SliceRegistry


def test_filter_does_not_leak_registry():
    reg = SliceRegistry()
    reg.add(KnowledgeSlice("W03-01", "copy", ("gil",)))
    reg.add(KnowledgeSlice("W03-02", "reg", ("uv",)))
    found = reg.by_tag("gil")
    assert [s.slice_id for s in found] == ["W03-01"]
    found.append(KnowledgeSlice("hack", "x", ("gil",)))
    assert [s.slice_id for s in reg.by_tag("gil")] == ["W03-01"]
```

用手摸：把 `DailySession` 的 tags 喂进去——同一概念，两个类型。

```powershell
uv run python -c "from pyforge.session import DailySession; from pyforge.slices import KnowledgeSlice, SliceRegistry; s=DailySession('W03').start(); s.tags.append('gil'); r=SliceRegistry(); r.add(KnowledgeSlice(s.slice_id, 'tonight', tuple(s.tags))); print([x.slice_id for x in r.by_tag('gil')])"
```

要看到：`['W03']`。Session 的 tags 是 list（当晚会变）；Slice 的 tags 是 tuple（进注册表就冻住）。

## 反例（必错）

```python
def leak(items, tag):
    return items  # 假装过滤，把内部 list 交出去
```

调用方 `leak(reg._items, "gil").clear()` 会把注册表清空。`by_tag` 必须返回新 list。

## 今晚只改这一刀

- `src/pyforge/slices.py`
- `tests/test_slices.py`

禁止：sqlite、文件落盘、把 Registry 做成单例全局。

## 验收命令

```powershell
uv run pytest tests/test_slices.py tests/test_session.py -q
```

## 下一课怎么接

`frozen` 和 tuple tags 先抄，W04-02 才拆为什么能当钥匙。下一课用 `CapabilityGate`：失败带着原因链，不是 `return False`。
