# W34-01 entry points

## 今晚能感觉到什么

`discover_plugins()` 能找到声明在 `[project.entry-points."pyforge.plugins"]` 的插件。不用 `pkg_resources`。

## 和 Java 不同的一句

Java：`ServiceLoader`。  
Python：`importlib.metadata.entry_points`。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_plugin_discover.py -q
```

## 反例（必错）

```python
import pkg_resources  # 已死，禁止
```

## 今晚只改这一刀

- discover
- `test_plugin_discover.py`

## 验收命令

```powershell
uv run pytest tests/test_plugin_discover.py -q
```

## 下一课怎么接

能发现了。下一课钩子隔离：插件炸不影响提交 session。
