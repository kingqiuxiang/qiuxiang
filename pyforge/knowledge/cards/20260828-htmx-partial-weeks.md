---
id: 20260828-htmx-partial-weeks
slice_id: W23
radar: [engineering]
---

# 场景

/weeks/ 做成 JSON API 喂 SPA，HTMX 拿不到 tbody。

# 反例

```text
/api/weeks/ JSON
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_week_list
```

# 关联代码路径

- src/forge_web/forge_web/views.py
