# W16-01 httpx 取消

## 今晚能感觉到什么

`fetch_catalog` 在超时或 cancel 时抛错，不留下半截“成功”。用 httpx mock，不上真爬虫。

## 和 Java 不同的一句

Java：取消常常是 `Future.cancel`。  
Python：`httpx` + `asyncio.CancelledError` / timeout。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_catalog_fetch.py -q
```

这是 **G4**。

## 反例（必错）

```python
requests.get(url)  # 同步、不可取消、还引入第二套 HTTP 库
```

## 今晚只改这一刀

- `src/pyforge/services/catalog.py`
- `tests/test_catalog_fetch.py`

禁止：真爬虫。

## 验收命令

```powershell
uv run pytest tests/test_gil_bench.py tests/test_gate_runner.py tests/test_catalog_fetch.py -q
```

## 下一课怎么接

远程索引有了。下一课把本机 sqlite schema 做成幂等 SQL 文件。
