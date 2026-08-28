# PyForge HANDOFF

下一任只读本文件 + `courses/curriculum.yaml` + `docs/ops/RUNBOOK.md`。禁止提问。禁止改 `IdeaProjects/mobile` 的 Java。

本文件是唯一交接真源。不要再写第二份 `docs/ops/handoff.md`。

```yaml
schema: pyforge-handoff-v1
updated_at: 2026-08-28T18:25:00+08:00
slice_id: W02-01
phase: daily
freeze_features: false
unmerged_slices: 1
inbox_pending: 0
allowed_paths:
  - src/pyforge/session.py
  - tests/test_session.py
  - HANDOFF.md
  - packs/_inbox.md
  - knowledge/cards/**
  - AGENTS.md
last_completed: "W01-02 过。tests/test_env.py 断言跑在 .venv；Cloud 上 uv prefix=/workspace/pyforge/.venv，系统 python3 prefix=/usr"
next_command: "读 courses/lessons/W02-01-DailySession与可变默认参数.md；新建 src/pyforge/session.py 与 tests/test_session.py；uv run pytest tests/test_session.py -q；uv run python -c \"from pyforge.session import DailySession; a=DailySession('W02').start(); b=DailySession('W02').start(); a.tags.append('gil'); print(a.tags, b.tags)\""
blocked: ""
do_not_touch:
  - 任何 mobile Java
  - 前端独立 SPA / Reflex 主栈
  - W1–20 的 django / frontend / Celery / Airflow
```

## 已锁

- **素材优先**：每晚先打开 `courses/lessons/` 里一篇，七段节奏不准换。没有本课 md 不准写代码。
- **体验**：每夜必须有能摸到的反馈（print / pytest 绿 / 文件出现）。
- **递进**：`DailySession` 主线不换名；允许新增值对象（Slice / Gate），不另开故事。
- 学习顺序：W1–20 = `src/pyforge` 内核（CLI + sqlite）。**G5 不过不准建 Django。**
- W21 起目标栈：Django 5 + 模板 + HTMX + 同进程 Ninja；编排先进程内 scheduler，Celery 不早于 G6
- 目标拆仓：11 个 app + `plugin_api` + `integrations/`（W21 才建 Django app）
- 行数：第一年核心协议 3–8 万；18 个月诚实 **25–35 万** qual LOC；100 万约 30–42 个月
- 日历真源：`courses/curriculum.yaml`（不是任何「W4 上 Django」的手册月历）
- 不做：FastAPI+React、双 ORM、vendor 第三方、未加载空壳、核心 `import integrations.*`、用 Python 自研 linter

贯穿对象：`DailySession` `KnowledgeSlice` `CapabilityGate` `CourseManifest` `PluginHook` `ObservationEvent`

## 当前切片

W02-01 DailySession 与可变默认参数（新建 `src/pyforge/session.py` + `tests/test_session.py`，tags 用 `default_factory`）

## 下一验收命令

```text
uv run pytest tests/test_session.py -q
```

## 完成定义

1. 上面验收命令绿，或 `blocked` 写清复现命令
2. `next_command` 已改成下一条**可执行**命令（禁止「继续完善」）
3. 一张四件套卡，或 `packs/_inbox.md` 草稿且 `inbox_pending` +1

## 禁止事项

改 mobile Java；提问；W1–20 出现 django/frontend/Celery；G1 没绿就建 `cli/`；allowed_paths 外改业务；测试红还加功能；上下文 80% 继续 Implement。

## 三行状态

- 做成了：W01-02 钉死项目 `.venv`；uv prefix 与系统 python3 prefix 不同；系统 `python3 -m pytest` 是 `No module named pytest`
- 红/绿：`uv run pytest tests/test_version.py tests/test_env.py -q` 2 passed
- 下一刀：W02-01，写下第一个领域对象 DailySession

## 云上（Cursor Cloud / qiuxiang）

- 本仓独立。禁止从公司 `mobile` / `qiangungun` 起 Cloud。
- VM 是 Ubuntu。开场永远贴 `AGENTS.md` 的「Cloud 开场」（不要写死切片号）。切片只看本文件 `slice_id`。
- 未 push 的提交 Cloud 看不见。基线必须带上最新本文件。
- 仓根 `.cursor/environment.json` 会装 `uv`；开机 `start` 会在有 `pyforge/` 时 `uv sync --frozen`。
- W01-02 实测：`uv run python` → `/workspace/pyforge/.venv`；`python3` → `/usr`

## 开场续跑

```text
继续 PyForge。不要提问，不要改任何 Java / mobile。
真源：pyforge/HANDOFF.md + courses/lessons/ 当前篇。先读 pyforge/AGENTS.md。
只改 pyforge/**。先读课文再写代码。W1–20 不准 django / frontend。
你在 Ubuntu Cloud VM。uv 不在 PATH 时：export PATH="$HOME/.local/bin:$PATH"
工作目录：cd /workspace/pyforge
先跑：uv run pytest tests/test_version.py tests/test_env.py -q
只做本文件 slice_id「今晚只改这一刀」，跑验收，回写 next_command，停。
```
