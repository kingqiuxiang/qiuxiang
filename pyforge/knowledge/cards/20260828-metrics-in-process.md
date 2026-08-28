---
id: 20260828-metrics-in-process
slice_id: W39
radar: [observe]
---

# 场景

计数直接接 SaaS APM 当作业。

# 反例

```text
SaaS APM
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_metrics
```

# 关联代码路径

- src/forge_web/forge_web/metrics.py
