---
id: 20260828-csv-one-transaction
slice_id: W19
radar: [data]
---

# 场景

CSV 一行一 commit，脏行前面的留下了。

# 反例

```text
for row in rows: conn.commit()
```

# 可验证命令

```bash
uv run pytest tests/test_import_events.py -q
```

# 关联代码路径

- src/pyforge/services/import_events.py
