---
id: 20260828-factory-not-playwright
slice_id: W25
radar: [engineering]
---

# 场景

工厂测试去开浏览器点按钮，pytest-django 一行就能进库。

# 反例

```text
Playwright 点 create
```

# 可验证命令

```bash
uv run pytest forge_web/tests/test_factories.py -q
```

# 关联代码路径

- src/forge_web/forge_web/tests/factories.py
