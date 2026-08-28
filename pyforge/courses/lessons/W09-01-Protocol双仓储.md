# W09-01 Protocol 双仓储

## 今晚能感觉到什么

同一段测试函数，既能喂 JSON 仓储，也能喂 sqlite 仓储。没有继承树。

## 和 Java 不同的一句

Java：`interface SessionRepository`。  
Python：`typing.Protocol` 是结构子类型，不用 `implements`。

## 示范（先原样跑）

`src/pyforge/domain/repos.py` 里 `SessionRepository` Protocol：`add` / `list_all`。

```powershell
uv run pytest tests/test_repo_protocol.py -q
```

## 反例（必错）

```python
class JsonRepo(SqliteSessionRepository):
    pass  # 为了“像 Java 继承”去继承实现类
```

## 今晚只改这一刀

- Protocol + 两个实现都满足它
- `tests/test_repo_protocol.py`

禁止：Django stubs。

## 验收命令

```powershell
uv run pytest tests/test_repo_protocol.py -q
```

## 下一课怎么接

下一课把仓储收成 `Repository[T]`，再加一个最小 Unit of Work。
