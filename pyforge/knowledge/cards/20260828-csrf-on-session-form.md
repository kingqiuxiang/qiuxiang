---
id: 20260828-csrf-on-session-form
slice_id: W24
radar: [engineering]
---

# 场景

HTMX 建 session 用 csrf_exempt，CSRF 门被拆掉。

# 反例

```text
csrf_exempt
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_session_form
```

# 关联代码路径

- src/forge_web/forge_web/forms.py
