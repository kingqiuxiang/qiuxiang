---
id: 20260828-wheel-version
slice_id: W31
radar: [engineering]
---

# 场景

把整个 .venv 打包当 wheel。

# 反例

```text
zip -r venv.zip .venv
```

# 可验证命令

```bash
uv run pyforge --version
```

# 关联代码路径

- src/pyforge/__init__.py
