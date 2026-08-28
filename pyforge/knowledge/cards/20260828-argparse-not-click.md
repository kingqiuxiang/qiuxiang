---
id: 20260828-argparse-not-click
slice_id: W06
radar: [engineering]
---

# 场景

为了像 Spring Shell 先装 click，标准库 argparse 其实已经能 start/list。

# 反例

```text
uv add click
```

# 可验证命令

```bash
uv run pytest tests/test_cli.py -q
```

# 关联代码路径

- src/pyforge/cli.py
