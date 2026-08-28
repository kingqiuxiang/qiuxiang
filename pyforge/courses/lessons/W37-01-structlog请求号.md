# W37-01 structlog request_id

## 今晚能感觉到什么

同一次请求的两条日志带同一个 `request_id`。不同请求不同号。不上 ELK。

## 和 Java 不同的一句

Java：MDC。  
Python：structlog contextvars。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_log_context.py -q
```

## 反例（必错）

```python
print("error")  # 无上下文，对不上请求
```

## 今晚只改这一刀

- `src/pyforge/services/log_context.py`
- `tests/test_log_context.py`

禁止：ELK。

## 验收命令

```powershell
uv run pytest tests/test_log_context.py -q
```

## 下一课怎么接

能追请求了。下一课 `pyforge_doctor` 检查环境。
