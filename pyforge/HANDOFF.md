# PyForge HANDOFF

下一任只读本文件 + `courses/curriculum.yaml` + `docs/ops/RUNBOOK.md`。禁止提问。禁止改 `IdeaProjects/mobile` 的 Java。

本文件是唯一交接真源。不要再写第二份 `docs/ops/handoff.md`。

```yaml
schema: pyforge-handoff-v1
updated_at: 2026-08-28T20:40:00+08:00
slice_id: W23-02
phase: daily
freeze_features: false
unmerged_slices: 0
inbox_pending: 0
allowed_paths:
  - HANDOFF.md
  - packs/_inbox.md
  - knowledge/cards/**
last_completed: "W23-02 锻造台：开工/收工/删除/点 tag，台账/课表/门禁，收工打番茄+错题 tag。"
next_command: "cd pyforge && export PATH=\"$HOME/.local/bin:$PATH\" && uv run python src/forge_web/manage.py test forge_web.tests.test_week_list forge_web.tests.test_session_lifecycle forge_web.tests.test_board"
blocked: ""
do_not_touch:
  - 任何 mobile Java
  - 前端独立 SPA / Reflex 主栈
  - 新开第二产品
```

## 已锁

- **素材优先**：每晚先打开 `courses/lessons/` 里一篇，七段节奏不准换。没有本课 md 不准写代码。
- **体验**：每夜必须有能摸到的反馈（print / pytest 绿 / 文件出现）。
- **递进**：`DailySession` 主线不换名；允许新增值对象（Slice / Gate），不另开故事。
- 学习顺序：W1–20 = `src/pyforge` 内核（CLI + sqlite）。**G5 不过不准建 Django。**
- W21 起目标栈：Django 5 + 模板 + HTMX + 同进程 Ninja；编排先进程内 scheduler，Celery 不早于 G6
- 不做：FastAPI+React、双 ORM、vendor 第三方、未加载空壳、核心 `import integrations.*`、用 Python 自研 linter

贯穿对象：`DailySession` `KnowledgeSlice` `CapabilityGate` `CourseManifest` `PluginHook` `ObservationEvent`

## 当前切片

48 周闭环。之后只修红 / 还债，不加第二条产品线。

## 下一验收命令

```text
pwsh -File scripts/verify_all.ps1
```

## 完成定义

1. 上面验收命令绿，或 `blocked` 写清复现命令
2. `next_command` 已改成下一条**可执行**命令（禁止「继续完善」）
3. 一张四件套卡，或 `packs/_inbox.md` 草稿且 `inbox_pending` +1

## 三行状态

- 做成了：课文+实现+卡齐；`scripts/verify_all.ps1` 把课表验收收成一条
- 红/绿：以 `pwsh -File scripts/verify_all.ps1` 为准
- 下一刀：只修红。不要新开第二产品

## 云上

当前分支 `cursor/w01-02-uv-env-f4a6` 当主线。未 push 的改动 Cloud 看不见。
