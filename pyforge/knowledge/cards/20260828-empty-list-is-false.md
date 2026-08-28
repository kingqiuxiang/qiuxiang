---
id: 20260828-empty-list-is-false
slice_id: W02-02
radar: [syntax]
---

# 场景

还没打 tag 的 `DailySession` 用 `if session.tags` 判断「有没有课」，空列表和 None 都被吞成没课，和 Java 空 ArrayList 仍是真对象相反。

# 反例

```python
def looks_java(items):
    if items:
        return "有课"
    return "没课"

print(looks_java([]))
print(looks_java(None))
```

期望：两行都是「没课」，空和 None 分不清。

# 可验证命令

```bash
uv run pytest tests/test_session.py -q
uv run python -c "from pyforge.session import DailySession; s=DailySession('W02'); print(bool(s.tags), s.has_tags())"
```

# 关联代码路径

- src/pyforge/session.py
- tests/test_session.py
