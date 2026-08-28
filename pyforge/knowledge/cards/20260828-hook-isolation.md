---
id: 20260828-hook-isolation
slice_id: W35
radar: [plugin]
---

# 场景

钩子不包 try，插件一炸 session 丢了。

# 反例

```text
hook()  # 不隔离
```

# 可验证命令

```bash
uv run pytest tests/test_plugin_isolation.py -q
```

# 关联代码路径

- src/pyforge/plugins/registry.py
