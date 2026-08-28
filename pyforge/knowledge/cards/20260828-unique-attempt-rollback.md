---
id: 20260828-unique-attempt-rollback
slice_id: W18
radar: [data]
---

# 场景

gate_attempts 重复插入被 except: pass 吞掉，门禁记录撒谎。

# 反例

```text
except Exception: pass
```

# 可验证命令

```bash
uv run pytest tests/test_gate_attempts.py -q
```

# 关联代码路径

- src/pyforge/services/attempts.py
