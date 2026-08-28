---
id: 20260828-backup-restore-point
slice_id: W44
radar: [data]
---

# 场景

备份传到网盘并自建 PKI。

# 反例

```text
云盘 PKI
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_backup_restore
```

# 关联代码路径

- src/forge_web/forge_web/backup.py
