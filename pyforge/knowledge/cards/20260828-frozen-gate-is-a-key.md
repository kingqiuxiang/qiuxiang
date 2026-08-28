---
id: 20260828-frozen-gate-is-a-key
slice_id: W04-02
radar: [syntax]
---

# 场景

把默认可变的 dataclass 丢进 set，当场 TypeError: unhashable。Java Lombok @Data 还能当 map key 然后脏桶；Python 第一步就拒。值对象要 frozen，DailySession 实体不冻。

# 反例

```python
from dataclasses import dataclass

@dataclass
class SoftGate:
    name: str

g = SoftGate("w04")
s = {g}
```

期望：`TypeError: unhashable type: 'SoftGate'`。

# 可验证命令

```bash
uv run pytest tests/test_session.py tests/test_copying.py tests/test_slices.py tests/test_gate.py -q
uv run python -c "from pyforge.gate import CapabilityGate; a=CapabilityGate('w04'); b=CapabilityGate('w04'); print(len({a,b}))
try:
    a.name='x'
except Exception as e:
    print(type(e).__name__)"
```

# 关联代码路径

- src/pyforge/gate.py
- tests/test_gate.py
- src/pyforge/session.py
