---
id: 20260828-atomic-export
slice_id: W08
radar: [engineering]
---

# 场景

导出时直接往目标文件追加半截 JSON，崩溃后文件既不是旧的也不是新的。

# 反例

```text
dest.write_text('[')
```

# 可验证命令

```bash
uv run pytest tests/test_export.py -q
```

# 关联代码路径

- src/pyforge/export.py
