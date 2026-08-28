---
id: 20260828-gate-engine-writes
slice_id: W42
radar: [engineering]
---

# 场景

门禁失败不写库，只 print。

# 反例

```text
print('fail')
```

# 可验证命令

```bash
uv run pytest tests/test_gate_engine.py -q
```

# 关联代码路径

- src/pyforge/services/gate_engine.py
