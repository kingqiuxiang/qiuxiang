---
id: 20260828-uv-lock-frozen
slice_id: W32
radar: [engineering]
---

# 场景

手改 uv.lock 一个 hash，frozen 复现失败。

# 反例

```text
手改 uv.lock
```

# 可验证命令

```bash
uv sync --frozen && uv run pytest -q
```

# 关联代码路径

- uv.lock
