---
id: 20260828-generic-repository
slice_id: W10
radar: [syntax]
---

# 场景

Session 和 Slice 各写一套仓储方法，T 被擦成 Any。

# 反例

```text
def get(id): return None
```

# 可验证命令

```bash
uv run pytest tests/test_generic_repo.py -q
```

# 关联代码路径

- src/pyforge/domain/generic.py
