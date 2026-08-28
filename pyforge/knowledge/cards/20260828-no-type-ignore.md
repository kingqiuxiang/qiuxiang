---
id: 20260828-no-type-ignore
slice_id: W11
radar: [engineering]
---

# 场景

mypy 红了就 type: ignore，门禁变成摆设。

# 反例

```text
x = 1  # type: ignore
```

# 可验证命令

```bash
uv run mypy src/pyforge/domain src/pyforge/services
```

# 关联代码路径

- src/pyforge/domain
