---
id: 20260828-query-budget-eight
slice_id: W45
radar: [data]
---

# 场景

周列表 for week in weeks: week.sessions.all() 打出 N+1。

# 反例

```text
N+1 循环
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_query_budget
```

# 关联代码路径

- src/forge_web/forge_web/views.py
