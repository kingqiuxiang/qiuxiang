---
id: 20260828-gil-is-visible
slice_id: W13
radar: [async]
---

# 场景

用 time.sleep 两线程假装并行变快，测的不是 GIL。

# 反例

```text
time.sleep(0.1)
```

# 可验证命令

```bash
uv run pytest tests/test_gil_bench.py -q
```

# 关联代码路径

- src/pyforge/services/gil_bench.py
