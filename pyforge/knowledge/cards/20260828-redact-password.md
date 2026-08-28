---
id: 20260828-redact-password
slice_id: W40
radar: [observe]
---

# 场景

登录失败把 password 打进日志。

# 反例

```text
log.info(payload)
```

# 可验证命令

```bash
uv run pytest tests/test_error_redact.py -q
```

# 关联代码路径

- src/pyforge/services/redact.py
