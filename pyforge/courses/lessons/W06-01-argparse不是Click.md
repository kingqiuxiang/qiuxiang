# W06-01 argparse 不是 Click

## 今晚能感觉到什么

`uv run python -m pyforge session start W06` 落盘一条；`session list` 能印出 `W06`。

## 和 Java 不同的一句

Java：Picocli / Spring Shell。  
Python：标准库 `argparse` 就够。W1–20 不上 click/typer。

## 示范（先原样跑）

`src/pyforge/cli.py` 提供 `main(argv: list[str] | None) -> int`。测试直接调 `main`，不要真开子进程。

```powershell
uv run pytest tests/test_cli.py -q
```

## 反例（必错）

```bash
uv add click
# 今晚禁止。标准库先走通
```

## 今晚只改这一刀

- `src/pyforge/cli.py`
- `src/pyforge/__main__.py`
- `tests/test_cli.py`

禁止：把 JSON 换成 sqlite（下一课才换）。

## 验收命令

```powershell
uv run pytest tests/test_cli.py -q
```

## 下一课怎么接

CLI 仍叫 `session`。下一课仓储换成 sqlite 事务，对象名还是 `DailySession`。
