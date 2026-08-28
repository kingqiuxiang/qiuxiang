---
id: 20260828-raise-from-keeps-cause
slice_id: W04-01
radar: [syntax]
---

# 场景

门禁失败只 `return False` 或裸 `raise GateFailed`，堆栈有两层却没有 `__cause__`。光秃 `except:` 还会吞掉 KeyboardInterrupt。

# 反例

```python
try:
    1 / 0
except Exception:
    raise GateFailed("w04")
```

期望：`__cause__` 是 None；连接句是 During handling，不是 direct cause。

# 可验证命令

```bash
uv run pytest tests/test_gate.py -q
uv run python -c "from pyforge.gate import CapabilityGate, GateFailed
try:
    CapabilityGate('w04').run(lambda: 1/0)
except GateFailed as e:
    print('cause', type(e.__cause__).__name__)"
```

# 关联代码路径

- src/pyforge/gate.py
- tests/test_gate.py
