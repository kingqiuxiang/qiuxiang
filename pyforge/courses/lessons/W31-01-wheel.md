# W31-01 wheel

## 今晚能感觉到什么

`uv build` 出 wheel。隔离环境只装 wheel，`pyforge --version` 印出版本。

## 和 Java 不同的一句

Java：jar。  
Python：wheel。不搭私服。

## 示范（先原样跑）

```powershell
uv run pyforge --version
```

这是 **G8** 的预演；G8 命令就是 `pyforge --version`。

## 反例（必错）

```text
把 .venv 整个打进压缩包当发布
```

## 今晚只改这一刀

- 确认 `--version` 走 `__version__`

## 验收命令

```powershell
uv run pyforge --version
```

## 下一课怎么接

锁文件要能 `--frozen` 复现。
