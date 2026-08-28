# W32-01 uv.lock frozen

## 今晚能感觉到什么

`uv sync --frozen` 成功，再 `pytest -q` 绿。不要和 Poetry 互迁。

## 和 Java 不同的一句

Java：锁在 `pom` + 私服。  
Python：`uv.lock` 是真源。改依赖必须更新锁。

## 示范（先原样跑）

```powershell
uv sync --frozen && uv run pytest -q
```

这是 **G8** 周边：命令 `pyforge --version` 仍要绿。

## 反例（必错）

```text
手改 uv.lock 一个 hash——frozen 会红
```

## 今晚只改这一刀

- 锁与 pyproject 对齐

## 验收命令

```powershell
uv sync --frozen && uv run pytest -q
```

## 下一课怎么接

内核可复现了。下一课插件 Protocol：`PluginRegistry`。
