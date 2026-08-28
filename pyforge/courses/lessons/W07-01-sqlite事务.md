# W07-01 sqlite3 事务

## 今晚能感觉到什么

插入两条，`list_all` 两条都在。插入到一半抛错，库里一条都没有。

## 和 Java 不同的一句

Java：Spring `@Transactional` 或自己 `conn.setAutoCommit(false)`。  
Python：`with conn:` 就是一个事务。不要上 SQLAlchemy。

## 示范（先原样跑）

`src/pyforge/sqlite_repo.py`。测试用临时 db 文件。

```powershell
uv run pytest tests/test_sqlite_repo.py -q
```

## 反例（必错）

```python
conn.execute("INSERT ...")
conn.execute("INSERT ...")  # 不 commit，换连接就丢
```

## 今晚只改这一刀

- `SqliteSessionRepository`
- `tests/test_sqlite_repo.py`

禁止：Postgres、SQLAlchemy。

## 验收命令

```powershell
uv run pytest tests/test_sqlite_repo.py -q
```

## 下一课怎么接

仓储能读了。下一课用生成器导出，并原子写文件（先写 tmp 再 replace）。
