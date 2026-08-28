---
id: 20260828-editable-install
slice_id: W29
radar: [engineering]
---

# 场景

用 conda 另开环境，可编辑安装进不去。

# 反例

```text
conda create -n pyforge
```

# 可验证命令

```bash
uv run python -c "import pyforge; assert pyforge.__version__"
```

# 关联代码路径

- pyproject.toml
