---
id: 20260828-module-is-not-a-class-file
slice_id: W01-01
radar: [syntax]
---

# 场景

刚用 uv 建好包，按 Java 习惯在 `__init__.py` 里写 `class Pyforge`，结果 `import pyforge` 能过，但版本号是模块属性，取 `pyforge.__version__` 直接 AttributeError。

# 反例

```python
class Pyforge:
    pass
```

期望：`import pyforge; print(pyforge.__version__)` 报 AttributeError。

# 可验证命令

```powershell
uv run pytest tests/test_version.py -q
uv run python -c "import pyforge; print(pyforge.__version__)"
```

# 关联代码路径

- src/pyforge/__init__.py
- tests/test_version.py
