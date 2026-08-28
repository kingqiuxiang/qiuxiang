# qiuxiang 仓约定

本仓有两块，**不要混着改**：

| 目录 | 是什么 | Cloud / Agent 改哪里 |
|---|---|---|
| 仓库根、`server/`、`web/` | 灵测 LingCe | 只有明确做接口智测时才动 |
| `pyforge/` | Python 自学锻炉 | **当前默认活在这里** |

当前切片：**只信 `pyforge/HANDOFF.md` 的 `slice_id`**，不要信任何过期的 W01-xx 口头。  
先读 `pyforge/AGENTS.md` 和 `pyforge/courses/lessons/` 对应课文。  
只改 `pyforge/**`（云环境脚本可改 `.cursor/**`）。禁止改 `server/`、`web/`、`idea-plugin/`，禁止动 Java。

云上开场整段贴 `pyforge/AGENTS.md` 的「Cloud 开场」。基线分支必须已经包含最新 `HANDOFF.md`。
