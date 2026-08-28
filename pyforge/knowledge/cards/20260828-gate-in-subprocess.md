---
id: 20260828-gate-in-subprocess
slice_id: W14
radar: [async]
---

# 场景

门禁死循环跑在本进程，整个 CLI 一起卡死。

# 反例

```text
fn()  # 本进程
```

# 可验证命令

```bash
uv run pytest tests/test_gate_runner.py -q
```

# 关联代码路径

- src/pyforge/services/gate_runner.py
