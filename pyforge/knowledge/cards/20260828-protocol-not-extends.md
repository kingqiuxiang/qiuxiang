---
id: 20260828-protocol-not-extends
slice_id: W09
radar: [syntax]
---

# 场景

为了像 Java implements，让 JsonRepo 去继承 Sqlite 实现类。

# 反例

```text
class JsonRepo(SqliteSessionRepository)
```

# 可验证命令

```bash
uv run pytest tests/test_repo_protocol.py -q
```

# 关联代码路径

- src/pyforge/domain/repos.py
