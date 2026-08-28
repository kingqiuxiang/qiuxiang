---
id: 20260828-httpx-can-cancel
slice_id: W16
radar: [async]
---

# 场景

用 requests.get 拉目录，超时只能杀线程。

# 反例

```text
requests.get(url)
```

# 可验证命令

```bash
uv run pytest tests/test_catalog_fetch.py -q
```

# 关联代码路径

- src/pyforge/services/catalog.py
