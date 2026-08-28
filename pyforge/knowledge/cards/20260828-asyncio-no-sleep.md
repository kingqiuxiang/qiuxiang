---
id: 20260828-asyncio-no-sleep
slice_id: W15
radar: [async]
---

# 场景

异步拉索引里写 time.sleep，事件循环被堵住。

# 反例

```text
time.sleep(0.1)
```

# 可验证命令

```bash
uv run pytest tests/test_async_index.py -q
```

# 关联代码路径

- src/pyforge/services/async_index.py
