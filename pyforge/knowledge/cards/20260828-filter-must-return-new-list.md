---
id: 20260828-filter-must-return-new-list
slice_id: W03-02
radar: [syntax]
---

# 场景

写过滤时 `return self._items`，调用方 append 或 clear 会直接改注册表。Session 的 tags 是会变的 list，进注册表必须冻成 tuple。

# 反例

```python
def leak(items, tag):
    return items
```

期望：`leak(reg._items, "gil").clear()` 把注册表清空。

# 可验证命令

```bash
uv run pytest tests/test_slices.py tests/test_session.py -q
uv run python -c "from pyforge.session import DailySession; from pyforge.slices import KnowledgeSlice, SliceRegistry; s=DailySession('W03').start(); s.tags.append('gil'); r=SliceRegistry(); r.add(KnowledgeSlice(s.slice_id, 'tonight', tuple(s.tags))); print([x.slice_id for x in r.by_tag('gil')])"
```

# 关联代码路径

- src/pyforge/slices.py
- tests/test_slices.py
- src/pyforge/session.py
