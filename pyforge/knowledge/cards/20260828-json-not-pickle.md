---
id: 20260828-json-not-pickle
slice_id: W05
radar: [engineering]
---

# 场景

把 DailySession 当 Java 对象直接 pickle 落盘，换版本就打不开。

# 反例

```text
pickle.dumps(session)
```

# 可验证命令

```bash
uv run pytest tests/test_session_store.py -q
```

# 关联代码路径

- src/pyforge/session_store.py
