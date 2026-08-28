# 内部评审 · 2026-08-28

轴：体验 / 递进 / 素材。不评百万行。

## 必须先修（P0）

| 文件 | 现象 | 建议 |
|---|---|---|
| W01-01 | 目录里已有 README/HANDOFF/courses。`uv init` 在非空仓常失败或生成不认 `src/` 的布局，`import pyforge` 第一夜就会红。 | 改成「已有仓：跳过 init；手写 pyproject 的 src 布局」并给出可粘贴的 `[tool.setuptools.packages.find] where=["src"]`（或 uv 等价）。示范命令必须是一条能绿的路径，不能赌 uv init 脾气。 |
| W01-01 | 课文假设 `src/pyforge/`，uv 默认不一定是 src layout。 | 把 pyproject 片段写死在课文里，或改用 `python -m pyforge` 前先 `uv add --dev pytest` + editable install。 |
| W01-02 | `pip install django` 当反例会真往系统灌包，体验不丝滑、还有副作用。 | 改成只对比两条 `sys.prefix`；系统没 `python` 时写明「命令失败也算看懂」。禁止真装 Django。 |

## 递进断裂（P1）

| 文件 | 现象 | 建议 |
|---|---|---|
| W02-02 → W03-01 | 上篇说下一课是 `b = a.tags[:]`，下篇却用独立的 `rows: list[list[str]]`。 | 要么 tags 做成一层演示（不够「看见内层」），要么改「下一课怎么接」的句子，对准 copying.rows。 |
| W03-01 / W03-02 | 浅拷贝和 Registry 都写 `tests/test_slices.py`，名字骗人对齐 slices。 | 浅拷贝测放到 `tests/test_copying.py`。 |
| W02-01 | 写「先红再绿」，正文却一次贴出正确实现。 | 拆成两步：先贴 `tags=[]` 让测试红，再改 `default_factory`。这才是体验。 |
| W02-01 | `_empty_tags` 多余；野外是 `default_factory=list`。 | 正文用 `list`，反例用 `tags=[]`，对照更狠。 |
| W1→W2 | 一夜之间出现 `datetime \| None`、`from __future__ import annotations`、aware UTC，无铺垫。 | W02-01 用字符串时间或注释一行「`\|` 是 3.10 联合类型，当 Java 的 `T \| null`」。 |

## 素材质量（P1/P2）

| 级别 | 文件 | 现象 | 建议 |
|---|---|---|---|
| P1 | W01-02 | `uv run pytest` 下 `test_env` 几乎恒绿，测的不是「分裂」，是 tautology。 | 测试只断言 `sys.executable`；分裂用两行 print 当「用手摸」，不要假装单测覆盖了系统 python。 |
| P1 | W04-01 | 反例说「没有 from，`__cause__` 常为 None」。3.11+ 在部分路径会有 `__context__`，学员可能看见另一条链以为课文错了。 | 改成「必须看 `__cause__`；没有 `from` 时 `__cause__` 是 None，`__context__` 可能仍在」。 |
| P2 | W01-01 | `class __init__` 太怪，不像 Java 人会写的错。 | 改成在 `__init__.py` 里 `class Pyforge:` 然后去找 `Pyforge.__version__`。 |
| P2 | W02-02 | `a is b` 对 1000 的结果依启动方式而变，课文已加「以实测为准」，仍容易吵。 | 删这条，或改成 `is None` 才是今晚的点。 |
| P2 | curriculum.yaml | 一周一行，和「一夜一篇」八课对不齐。 | yaml 加 `nights` 或声明「W1 = W01-01+W01-02」。 |
| P2 | 全套 | 还没有 `pyforge lesson` 播放器，流程是「自己开 md」。 | 体验上可后补；W1 先把命令跑通比做播放器重要。 |

## 我认为可以后补

1. W5 落盘课、播放器、雷达、freeze 手册。  
2. Java 对照再扩 15 条（已够用的先别加）。  
3. 把示范代码提成仓内真文件，课文改成「打开这个文件跑」——现在是复制粘贴，够用但不够丝滑。

## 总判

方向对：七段节奏、一条对象链、Java 对照，比 11 个 app 的规划更像教材。  
第一夜过不了 src/import，后面八篇都是纸上谈兵。先修 W01-01 的建仓命令，再谈递进打磨。
