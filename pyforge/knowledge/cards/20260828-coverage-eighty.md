---
id: 20260828-coverage-eighty
slice_id: W26
radar: [engineering]
---

# 场景

为空测试刷行数骗过 80%，内核仍没被用到。

# 反例

```text
assert True
```

# 可验证命令

```bash
uv run pytest --cov=src/pyforge --cov-fail-under=80 -q
```

# 关联代码路径

- src/pyforge
