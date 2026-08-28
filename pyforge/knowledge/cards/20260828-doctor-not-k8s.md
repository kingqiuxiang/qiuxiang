---
id: 20260828-doctor-not-k8s
slice_id: W38
radar: [observe]
---

# 场景

doctor 做成 K8s liveness 玄学。

# 反例

```text
K8s probe
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py pyforge_doctor
```

# 关联代码路径

- src/forge_web/forge_web/management/commands/pyforge_doctor.py
