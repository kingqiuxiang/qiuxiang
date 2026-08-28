---
id: 20260828-plugin-protocol
slice_id: W33
radar: [plugin]
---

# 场景

每个插件起一个 FastAPI 进程。

# 反例

```text
插件微服务
```

# 可验证命令

```bash
uv run pytest tests/test_plugin_registry.py -q
```

# 关联代码路径

- src/pyforge/plugins/registry.py
