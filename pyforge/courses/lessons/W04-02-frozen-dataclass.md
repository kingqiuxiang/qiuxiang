# W04-02 frozen dataclass：才能当钥匙

## 今晚能感觉到什么

两个同名的 `CapabilityGate` 放进 `set`，长度是 1。你改 `.name` 会立刻 `FrozenInstanceError`。默认可变的 dataclass **进不了 set**——当场 `TypeError`，不是「改完字段再丢桶」。

## 和 Java 不同的一句

Java：Lombok `@Data` 默认可变，还能当 map key，改字段后桶就丢了。你们 Java 仓已经禁 Lombok。  
Python：`@dataclass` 默认可变，**直接拒绝**当 dict/set 的 key（`unhashable`）。要当钥匙：`frozen=True`。

## 示范（先原样跑）

确认 `CapabilityGate` 已是 `frozen=True`。追加测试：

```python
from dataclasses import FrozenInstanceError

from pyforge.gate import CapabilityGate


def test_gate_is_hashable_value():
    a = CapabilityGate("w04")
    b = CapabilityGate("w04")
    bag = {a, b}
    assert len(bag) == 1


def test_cannot_mutate_gate_name():
    g = CapabilityGate("w04")
    try:
        g.name = "hack"
    except FrozenInstanceError:
        return
    raise AssertionError("expected FrozenInstanceError")
```

用手摸：

```powershell
uv run python -c "from pyforge.gate import CapabilityGate; a=CapabilityGate('w04'); b=CapabilityGate('w04'); print(len({a,b}))
try:
    a.name='x'
except Exception as e:
    print(type(e).__name__)"
```

要看到：`1` 和 `FrozenInstanceError`。

## 反例（必错，已按本机行为写）

```python
from dataclasses import dataclass

@dataclass
class SoftGate:
    name: str

g = SoftGate("w04")
s = {g}  # TypeError: unhashable type: 'SoftGate'
```

Python 不演「先放进 set 再改字段、`g in s` 变 False」那场戏——可变 dataclass 在第一步就拒。这正是要 `frozen=True` 的原因。

若你强行 `@dataclass(eq=True, unsafe_hash=True)`，才能看到 Java 那种脏桶；今晚不必写，知道有这条歪路就行。

`DailySession` **不要** frozen——它今晚会变 `tags`、会 `start/stop`。值对象才冻。实体不冻。

## 今晚只改这一刀

- 测试加两条
- `CapabilityGate` 保持 frozen
- `DailySession` 保持可变

这是 G1 收口：session / slices / gate 必须一起绿。

## 验收命令

```powershell
uv run pytest tests/test_session.py tests/test_copying.py tests/test_slices.py tests/test_gate.py -q
```

这就是 G1。不过，不准建 `cli/`。

## 下一课怎么接

G1 过后，同一份 `DailySession` 开始**落盘成 json**（W05）。对象名不换。流程还是：打开下一篇 → 示范 → 反例 → 一刀 → 验收。
