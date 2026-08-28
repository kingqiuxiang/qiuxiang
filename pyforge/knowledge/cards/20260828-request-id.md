---
id: 20260828-request-id
slice_id: W37
radar: [observe]
---

# 场景

错误日志没有 request_id，两条日志对不上同一次请求。

# 反例

```text
print('error')
```

# 可验证命令

```bash
uv run pytest tests/test_log_context.py -q
```

# 关联代码路径

- src/pyforge/services/log_context.py
