# PyForge 运转（薄手册）

交接真源永远是仓库根目录 `HANDOFF.md`。本文件只补日/周/月怎么跑。  
日历真源：`courses/curriculum.yaml`。**作废**任何「2026-09 W04 上 Django」的月历。

## 状态机（第一条命中就做，做完停）

1. 先打开 `HANDOFF.md` 里 `slice_id` 对应的 `courses/lessons/` 那一篇，再执行文中示范。没有本课 md 不准写代码。
2. `freeze_features: true` 或切片测试红 → 只修红 / 还债。
3. 上下文 ≥80% → 只改 HANDOFF，开新 Chat。
4. 周日 → 选下周 1 个切片 + 加 1 道回归。
5. 周六 → 回顾 + 整理 diff（未听到提交就不 commit）。
6. 工作日 → 75 分钟：学 17 / 写 35 / 测 13 / 入库 10。

## 时间盒

工作日 75（下限 60 / 上限 90，多出来只加深本切片）。周末单日 150。月末审 180，当天不加功能。到点停。

## freeze（命中任一）

切片红；周回归红；未合并切片 ≥2；inbox ≥3；债务头票 >14 天或开放票 >7；无测试插件仍 enabled；上下文 80%。

## 知识卡四字段

场景（谁在什么情况下做错，≥20 字）/ 反例（带期望的命令）/ 可验证命令 / 本仓路径。  
缺一写 `packs/_inbox.md` 或 `knowledge/_inbox/`，`inbox_pending += 1`。

## 评测金字塔

单元 `tests/unit` → 切片 `tests/slices/wXX` → 周回归每周 +1 → 月雷达六维（语法/工程/异步/数据/插件/观测）。  
W1 还没有这些目录时，验收就是 HANDOFF 里那一条 pytest。

## Cursor Cloud

VM 是 Ubuntu。开场整段贴 `AGENTS.md` 的「Cloud 开场」。不要从 `mobile` 仓起 Cloud。课文 PowerShell 当 bash 跑。

## 和其它文档打架时

`HANDOFF.md` 已锁条目 > 本 RUNBOOK > `courses/curriculum.yaml` > 聊天新想法。  
新想法进债务，不当晚做。
