---
id: 20260828-orm-three-tables
slice_id: W22
radar: [data]
---

# 场景

为了对齐 JPA 去写自定义 Manager，三张表还没 migrate。

# 反例

```text
class SessionManager
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_models
```

# 关联代码路径

- src/forge_web/forge_web/models.py
