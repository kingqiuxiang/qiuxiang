---
id: 20260828-uv-is-not-java-home
slice_id: W01-02
radar: [engineering]
---

# 场景

刚过完包能 import，按 JAVA_HOME 习惯用系统 `python3 -m pytest` 跑测试，结果系统解释器没有 pytest，包也装不到项目 venv。不进 `.venv`，装再多包也是装到别人家里。

# 反例

```bash
python3 -m pytest tests/test_env.py -q
```

期望：`No module named pytest`。不要为了凑绿去装全局包。

# 可验证命令

```bash
uv run pytest tests/test_version.py tests/test_env.py -q
uv run python -c "import sys; print('uv ', sys.prefix)"
python3 -c "import sys; print('sys', sys.prefix)"
```

# 关联代码路径

- tests/test_env.py
- tests/test_version.py
