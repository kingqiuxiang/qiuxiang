# W15-01 asyncio 假 HTTP

## 今晚能感觉到什么

`fetch_index` 并发拉两条假 URL，结果按 id 排好。事件循环里没有 `time.sleep`。

## 和 Java 不同的一句

Java：`CompletableFuture` / WebClient。  
Python：`asyncio.gather`。今晚用内存假传输，不上真网。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_async_index.py -q
```

## 反例（必错）

```python
import time
time.sleep(0.1)  # 阻塞事件循环，本课作废
```

## 今晚只改这一刀

- `src/pyforge/services/async_index.py`
- `tests/test_async_index.py`

禁止：真 HTTP。

## 验收命令

```powershell
uv run pytest tests/test_async_index.py -q
```

## 下一课怎么接

假传输换成 `httpx`，必须能取消。
