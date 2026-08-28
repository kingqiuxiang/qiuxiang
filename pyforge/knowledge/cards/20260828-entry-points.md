---
id: 20260828-entry-points
slice_id: W34
radar: [plugin]
---

# 场景

用已死的 pkg_resources 发现插件。

# 反例

```text
import pkg_resources
```

# 可验证命令

```bash
uv run pytest tests/test_plugin_discover.py -q
```

# 关联代码路径

- src/pyforge/plugins/discover.py
