---
id: 20260828-weekly-no-charts
slice_id: W20
radar: [data]
---

# 场景

为了周报好看去装 matplotlib，G5 被图表库带跑。

# 反例

```text
pip install matplotlib
```

# 可验证命令

```bash
uv run pytest tests/test_weekly_report.py -q
```

# 关联代码路径

- src/pyforge/services/weekly_report.py
