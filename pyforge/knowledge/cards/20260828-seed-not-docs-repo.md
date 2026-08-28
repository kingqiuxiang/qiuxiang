---
id: 20260828-seed-not-docs-repo
slice_id: W47
radar: [engineering]
---

# 场景

演示数据另开独立文档仓。

# 反例

```text
独立 docs 仓
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py seed_demo
```

# 关联代码路径

- src/forge_web/forge_web/management/commands/seed_demo.py
