# W05-01 JSON 落盘

## 今晚能感觉到什么

`save_session` 之后目录里多出一个 `.json`。再 `load_session`，`slice_id` 和 `tags` 回来了。没有 pickle，没有 YAML。

## 和 Java 不同的一句

Java：Jackson + `FileWriter`，路径常是 `String`。  
Python：`pathlib.Path` 是一等公民；`json` 只认 `dict/list/str/number/bool/null`，`datetime` 必须自己变成 ISO 字符串。

## 示范（先原样跑）

`src/pyforge/session_store.py` 用 `Path.write_text`。测试写到 `tmp_path`，不要写仓库。

```powershell
uv run pytest tests/test_session_store.py -q
```

用手摸：

```powershell
uv run python -c "from pathlib import Path; from pyforge.session import DailySession; from pyforge.session_store import save_session, load_session; p=Path('/tmp/w05.json'); s=DailySession('W05').start(); s.tags.append('json'); save_session(p,s); print(load_session(p).tags)"
```

要看到：`['json']`。

## 反例（必错）

```python
import pickle
pickle.dumps(DailySession("W05"))  # 今晚禁止。换机器、换版本都会咬人
```

## 今晚只改这一刀

- `src/pyforge/session_store.py`
- `tests/test_session_store.py`

禁止：sqlite、CLI、YAML。

## 验收命令

```powershell
uv run pytest tests/test_session_store.py -q
```

## 下一课怎么接

文件会写了。下一课用 argparse 把 `session start|end|list` 接到这份 JSON。
