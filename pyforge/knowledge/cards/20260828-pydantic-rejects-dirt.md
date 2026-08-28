---
id: 20260828-pydantic-rejects-dirt
slice_id: W12
radar: [data]
---

# 场景

CourseManifest 用裸 dict 装课表，缺 weeks 也静默进来。

# 反例

```text
return d
```

# 可验证命令

```bash
uv run pytest tests/test_course_manifest.py -q
```

# 关联代码路径

- src/pyforge/domain/manifest.py
