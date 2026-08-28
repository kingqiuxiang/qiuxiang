---
id: 20260828-slice-is-shallow
slice_id: W03-01
radar: [syntax]
---

# 场景

以为 `rows[:]` 是值拷贝，改内层元素后原表一起变。`b = a` 更糟，连外层 append 都会污染原名。

# 反例

```python
a = [["gil"]]
b = a
b.append(["new"])
print(a)
```

期望：`a` 也多一行。浅拷贝改内层：`shallow [['x']]`。

# 可验证命令

```bash
uv run pytest tests/test_copying.py -q
uv run python -c "from pyforge.copying import shallow_rows, deep_rows; a=[['gil']]; b=shallow_rows(a); b[0][0]='x'; print('shallow', a); a=[['gil']]; c=deep_rows(a); c[0][0]='x'; print('deep   ', a)"
```

# 关联代码路径

- src/pyforge/copying.py
- tests/test_copying.py
