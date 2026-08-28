# W33-01 插件 Protocol

## 今晚能感觉到什么

注册两个插件，`run_hook("on_session_stop", session)` 都收到。不是微服务。

## 和 Java 不同的一句

Java：SPI。  
Python：`Protocol` + 注册表。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_plugin_registry.py -q
```

## 反例（必错）

```text
每个插件一个 FastAPI 进程——禁止
```

## 今晚只改这一刀

- `src/pyforge/plugins/registry.py`
- `tests/test_plugin_registry.py`

## 验收命令

```powershell
uv run pytest tests/test_plugin_registry.py -q
```

## 下一课怎么接

注册表有了。下一课用 entry points 发现 `pyforge.plugins`。
