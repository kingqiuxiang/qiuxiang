---
id: 20260828-mutable-default-is-shared
slice_id: W02-01
radar: [syntax]
---

# 场景

按 Java 习惯给 `DailySession` 写 `tags=[]`，连续 `start()` 两条，往第一条 append，第二条 tags 也被污染，因为默认 list 只在定义时创建一次。

# 反例

```python
def broken_start(tags=[]):
    tags.append("x")
    return tags

print("第一次", broken_start())
print("第二次", broken_start())
```

期望：两次打印都是 `['x', 'x']`（同一个 list）。

# 可验证命令

```bash
uv run pytest tests/test_session.py -q
uv run python -c "from pyforge.session import DailySession; a=DailySession('W02').start(); b=DailySession('W02').start(); a.tags.append('gil'); print(a.tags, b.tags)"
```

# 关联代码路径

- src/pyforge/session.py
- tests/test_session.py
