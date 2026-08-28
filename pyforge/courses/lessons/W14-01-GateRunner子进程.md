# W14-01 GateRunner 子进程

## 今晚能感觉到什么

正常 `fn` 在子进程返回。超时的 `fn` 被杀掉，抛错，主进程还活着。不是 Celery。

## 和 Java 不同的一句

Java：进程外常用独立 JVM。  
Python：`ProcessPoolExecutor` + `future.result(timeout=...)`。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_gate_runner.py -q
```

## 反例（必错）

```python
fn()  # 在本进程跑死循环，整个 CLI 一起死
```

## 今晚只改这一刀

- `src/pyforge/services/gate_runner.py`
- `tests/test_gate_runner.py`

禁止：Celery。

## 验收命令

```powershell
uv run pytest tests/test_gate_runner.py -q
```

## 下一课怎么接

进程会了。下一课 asyncio 拉一份假课程索引，禁止 `time.sleep` 冒充异步。
