---
id: 20260828-yaml-eat-curriculum
slice_id: W41
radar: [data]
---

# 场景

自造 .pyforge 语言而不吃 curriculum.yaml。

# 反例

```text
发明 DSL
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py import_course courses/curriculum.yaml
```

# 关联代码路径

- src/forge_web/forge_web/management/commands/import_course.py
