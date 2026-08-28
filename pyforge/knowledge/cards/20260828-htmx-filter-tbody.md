---
id: 20260828-htmx-filter-tbody
slice_id: W43
radar: [engineering]
---

# 场景

筛选周列表上 WebSocket 推全页。

# 反例

```text
WebSocket
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_week_filter
```

# 关联代码路径

- src/forge_web/forge_web/views.py
