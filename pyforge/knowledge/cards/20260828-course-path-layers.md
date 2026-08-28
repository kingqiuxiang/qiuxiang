---
id: 20260828-course-path-layers
slice_id: W23
radar: [engineering]
---

# 场景

课表 48 行平铺，想由浅入深只能自己在脑子里分层，于是去新开文档站。

# 反例

```text
npx create-docusaurus 再抄一本开源书
```

# 可验证命令

```bash
uv run python src/forge_web/manage.py test forge_web.tests.test_course_path
```

# 关联代码路径

- courses/modules.yaml
- src/forge_web/forge_web/templates/forge_web/path.html
