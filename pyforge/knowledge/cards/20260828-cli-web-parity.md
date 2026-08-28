---
id: 20260828-cli-web-parity
slice_id: W27
radar: [engineering]
---

# 场景

CLI 写 JSON、Web 写另一张表，对账测试假装绿。

# 反例

```text
两套表各写各的
```

# 可验证命令

```bash
uv run pytest tests/test_parity_cli_web.py -q
```

# 关联代码路径

- tests/test_parity_cli_web.py
