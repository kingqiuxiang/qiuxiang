---
id: 20260828-console-scripts
slice_id: W30
radar: [engineering]
---

# 场景

用 Dockerfile 当发布，本机没有 pyforge 命令。

# 反例

```text
写 Dockerfile 当发布
```

# 可验证命令

```bash
uv run pyforge --help
```

# 关联代码路径

- src/pyforge/cli.py
