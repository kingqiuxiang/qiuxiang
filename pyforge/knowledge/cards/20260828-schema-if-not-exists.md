---
id: 20260828-schema-if-not-exists
slice_id: W17
radar: [data]
---

# 场景

schema 用 CREATE TABLE 没有 IF NOT EXISTS，第二次必炸。

# 反例

```text
CREATE TABLE sessions (...);
```

# 可验证命令

```bash
uv run pytest tests/test_schema.py -q
```

# 关联代码路径

- src/pyforge/sql/schema_v1.sql
