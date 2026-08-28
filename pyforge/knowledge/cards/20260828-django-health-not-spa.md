---
id: 20260828-django-health-not-spa
slice_id: W21
radar: [engineering]
---

# 场景

对照 Spring 先开 Vite frontend，/health/ 还没有。

# 反例

```text
新建 frontend/
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py check
```

# 关联代码路径

- src/forge_web/forge_web/views.py
