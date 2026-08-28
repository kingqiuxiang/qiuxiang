# W02-02 None 和空都是假

## 今晚能感觉到什么

`DailySession` 还没打 tag 时，`has_tags()` 是 `False`。你往里加一个 tag，变成 `True`。空列表不是「空的对象但仍为真」，它就是假。

## 和 Java 不同的一句

Java：`list` 只要不是 `null`，`if (items)` 这种写法都不存在；空 `ArrayList` 仍是 true 对象。  
Python：`None`、`[]`、`{}`、`""`、`0`、`False` **全是假**。`if session.tags` 会把「还没打标签」吞掉。要问空，写清楚 `== []` 或 `len` 或显式方法。

`None` 判断用 `is None`，不用 `== None`。`is` 比身份，`==` 比值。

## 示范（先原样跑）

在 `DailySession` 上加一个**不会撒谎**的方法：

```python
    def has_tags(self) -> bool:
        return len(self.tags) > 0
```

`tests/test_session.py` 追加：

```python
def test_empty_tags_are_not_has_tags():
    s = DailySession("W02")
    assert s.has_tags() is False
    s.tags.append("copy")
    assert s.has_tags() is True


def test_stop_without_start_raises():
    s = DailySession("W02")
    try:
        s.stop()
    except RuntimeError as exc:
        assert "not started" in str(exc)
    else:
        raise AssertionError("expected RuntimeError")
```

```powershell
uv run pytest tests/test_session.py -q
```

用手摸：

```powershell
uv run python -c "from pyforge.session import DailySession; s=DailySession('W02'); print(bool(s.tags), s.has_tags())"
```

要看到：`False False`。两个都假，但以后只准信 `has_tags()`——`bool(s.tags)` 太容易和「对象在不在」搞混。

## 反例（必错）

```python
def looks_java(items):
    if items:
        return "有课"
    return "没课"

print(looks_java([]))   # 没课 —— 空列表被吞
print(looks_java(None)) # 没课 —— None 也被吞，和空列表分不清
```

另一条：

```python
a = 1000
b = 1000
print(a == b, a is b)  # True False（以你解释器实测为准；小 int 可能驻留骗人）
```

不要用 `is` 比数字、比字符串。只对 `None` 用 `is`。

## 今晚只改这一刀

- `has_tags` + 上面两个测试
- 禁止把 `tags` 默认改回 `None` 来「更像 Java 的 Optional」

## 验收命令

```powershell
uv run pytest tests/test_session.py -q
```

## 下一课怎么接

`tags` 保持扁平 `list[str]`。下一课用独立的 `rows: list[list[str]]` 看浅拷贝：改内层，A 和 B 一起变。
