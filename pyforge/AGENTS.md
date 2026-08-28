# PyForge Agent 约定

独立仓。用造系统的方式学 Python。不要改 `IdeaProjects/mobile` 的任何 Java。

交接真源只有一份：根目录 `HANDOFF.md`。日历真源：`courses/curriculum.yaml`。运转薄手册：`docs/ops/RUNBOOK.md`。不要再写第二份 handoff。

## 先读再做

1. 读 `HANDOFF.md` 里的 `slice_id` / `next_command` / `allowed_paths`。
2. 打开 `courses/lessons/` 里对应那一篇。没有本课 md 不准写代码。
3. 只做课文「今晚只改这一刀」。跑验收。回写 `HANDOFF.md` 的 `next_command`。停。
4. 过不了验收就写 `blocked`（带复现命令），不要换切片、不要加框架。

当前进度（2026-08-28）：**W01-02 已绿**（`tests/test_env.py` 钉死 `.venv`）。**下一刀看 `HANDOFF.md` 的 `slice_id`**（现为 W02-01）。禁止再跑 `uv init`。切片号以 `HANDOFF.md` 为准，不要信本段过期数字。

## 命令

全程 `uv run`。不要 `source .venv/bin/activate`，不要 `Activate.ps1`。

```bash
export PATH="$HOME/.local/bin:$PATH"
cd pyforge 2>/dev/null || true
uv run pytest tests/test_version.py tests/test_env.py -q
uv run python -c "import pyforge; print(pyforge.__version__)"
```

课文里的 PowerShell 块：把 `uv run ...` 原样在 bash 跑。没有 `python` 就试 `python3`。不要写 `C:\` 路径。

## 已锁（不要再讨论）

- 素材优先，七段节奏不准换。
- `DailySession` 主线不换名；可加 `KnowledgeSlice` / `CapabilityGate`，不另开故事。
- **W1–20** 只做 `src/pyforge` 内核（CLI + sqlite）。**G5 不过不准建 Django。**
- W21 起才是 Django 5 + 模板 + HTMX + 同进程 Ninja。Celery 不早于 G6。
- 禁止：FastAPI+React、Reflex 主栈、双 ORM、vendor 注水、W1–20 的 `frontend/` / Celery / Airflow。

## 完成一刀

1. 验收命令绿，或 `blocked` 写清。
2. `HANDOFF.md` 的 `next_command` 改成下一条可执行命令（禁止「继续完善」）。
3. 一张四件套卡（`knowledge/cards/`），或缺项写 `packs/_inbox.md` 且 `inbox_pending +1`。

## Cursor Cloud specific instructions

Cloud Agent 跑在 **Ubuntu VM**，不是 Windows。本机 Windows 只负责 push。未 commit / 未 push 的改动不会上来。

- 命名空间按用户口头：**qiuxiang / pyforge**（Origin codebase 或 GitHub 个人仓，以你实际挂上的远端为准）。
- 仓在 `qiuxiang` 里时工作目录是 `/workspace/pyforge`。环境：仓根 `.cursor/environment.json` 会装 `uv` 并 `uv sync --frozen`。Agent 壳里若找不到 `uv`：`export PATH="$HOME/.local/bin:$PATH"`，再跑 `bash .cursor/install-pyforge.sh`（仓根）或 `bash .cursor/install.sh`（仓根就是 pyforge）。
- 起步自检（必须先绿再改课）：

```bash
export PATH="$HOME/.local/bin:$PATH"
cd /workspace/pyforge
uv run pytest tests/test_version.py tests/test_env.py -q
uv run python -c "import pyforge; print(pyforge.__version__)"
```

  要看到 passed 和 `0.1.0`。红了先修环境，不要开始本课代码。
- **永远读 `HANDOFF.md` 的 `slice_id`。** 只做那一刀。做完停。禁止连刷下一课。禁止按本文件里过期的「当前切片：W01-xx」开干。
- 课文 PowerShell 当 bash 跑。系统解释器用 `python3`。不要装全局包。
- 不要改无关文件，不要把 secret 写进仓，不要开 Django / frontend / CI 流水线。
- 在**新分支**提交。开 PR，写清改了什么、怎么测的。不要直接推 `main`。

### Cloud 开场（整段贴到 cursor.com/agents）

切片写在 `HANDOFF.md`，下面这段不要改切片号。

```text
继续 PyForge。不要提问，不要改任何 Java / mobile。
真源：pyforge/HANDOFF.md + courses/lessons/ 当前篇。先读 pyforge/AGENTS.md 的「Cursor Cloud specific instructions」。
只改 pyforge/**。禁止改 server/ web/ idea-plugin，禁止动 Java。
你在 Ubuntu Cloud VM。uv 不在 PATH 时：export PATH="$HOME/.local/bin:$PATH"
工作目录：cd /workspace/pyforge
先跑：uv run pytest tests/test_version.py tests/test_env.py -q  （必须绿，必须印出 0.1.0）
只做 HANDOFF.md 里 slice_id 那一课的「今晚只改这一刀」。没有本课 md 不准写代码。
不要改 __version__，不要 uv init，不要装全局包，不要连做下一课。
跑课文验收，回写 HANDOFF.md 的 next_command，补一张 knowledge/cards 四件套。新分支提交并开 PR。停。
```
