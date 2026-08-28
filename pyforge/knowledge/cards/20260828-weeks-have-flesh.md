---
id: 20260828-weeks-have-flesh
slice_id: W23
radar: [engineering]
---

# 场景

`/weeks/` 只有两个白输入，想开工收工就去新开 React 仓。

# 反例

```text
npx create-vite 再喂 /api/sessions
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_session_lifecycle forge_web.tests.test_board
```

# 关联代码路径

- src/forge_web/forge_web/actions.py
- src/forge_web/forge_web/templates/forge_web/weeks.html
