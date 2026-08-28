---
id: 20260828-verify-all-weeks
slice_id: W48
radar: [engineering]
---

# 场景

48 周都写过了，但本地 CI 只抽几条 pytest，课表里的 verify 文件删了也不红。

# 反例

```text
ci.ps1 只跑 G1 四条测试，就当闭环
```

# 可验证命令

```bash
pwsh -File scripts/verify_all.ps1
```

# 关联代码路径

- scripts/verify_all.ps1
- tests/test_curriculum_closed.py
