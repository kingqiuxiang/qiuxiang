# W17-01 schema 幂等

## 今晚能感觉到什么

`apply_schema` 连跑两次都不报错。表 `sessions` / `slices` / `gate_attempts` 都在。

## 和 Java 不同的一句

Java：Flyway 版本脚本。  
Python：今晚就是一份 `schema_v1.sql` + `CREATE TABLE IF NOT EXISTS`。不上 Django。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_schema.py -q
```

## 反例（必错）

```sql
CREATE TABLE sessions (...);  -- 第二次必炸
```

## 今晚只改这一刀

- `src/pyforge/sql/schema_v1.sql`
- `src/pyforge/services/schema.py`
- `tests/test_schema.py`

禁止：Django migration。

## 验收命令

```powershell
uv run pytest tests/test_schema.py -q
```

## 下一课怎么接

有表了。下一课给 `gate_attempts` 加唯一约束，冲突就整单回滚。
