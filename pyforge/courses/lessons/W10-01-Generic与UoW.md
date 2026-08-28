# W10-01 Generic 与 UoW

## 今晚能感觉到什么

`Repository[DailySession]` 和 `Repository[KnowledgeSlice]` 是同一个泛型壳。`UnitOfWork.commit()` 一次提交。

## 和 Java 不同的一句

Java：`Repository<T>`。  
Python：`Generic[T]` + `TypeVar`。Pydantic 留到下周。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_generic_repo.py -q
```

## 反例（必错）

```python
def get(id):  # 无类型，T 被擦成 Any，mypy 下周会骂
    return None
```

## 今晚只改这一刀

- `src/pyforge/domain/generic.py`
- `tests/test_generic_repo.py`

禁止：提前引入 Pydantic。

## 验收命令

```powershell
uv run pytest tests/test_generic_repo.py -q
```

## 下一课怎么接

类型已经写上。下一课 `mypy src/pyforge/domain src/pyforge/services` 必须零 `type: ignore`。
