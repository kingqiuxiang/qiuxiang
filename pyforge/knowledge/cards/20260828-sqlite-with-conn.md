---
id: 20260828-sqlite-with-conn
slice_id: W07
radar: [data]
---

# 场景

两条 INSERT 不放进 with conn，第二条失败时第一条已经看见。

# 反例

```text
conn.execute(insert); conn.execute(insert)  # 无事务
```

# 可验证命令

```bash
uv run pytest tests/test_sqlite_repo.py -q
```

# 关联代码路径

- src/pyforge/sqlite_repo.py
