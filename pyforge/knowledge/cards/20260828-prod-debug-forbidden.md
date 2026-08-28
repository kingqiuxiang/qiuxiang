---
id: 20260828-prod-debug-forbidden
slice_id: W46
radar: [observe]
---

# 场景

prod 环境 DEBUG=True 还说 ok。

# 反例

```text
doctor --env prod 在 DEBUG 下成功
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py pyforge_doctor --env prod
```

# 关联代码路径

- src/forge_web/forge_web/management/commands/pyforge_doctor.py
