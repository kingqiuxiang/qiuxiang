# 课（素材真源）

每晚只打开 **一篇**。顺序就是递进。同一对象 `DailySession` 从 W02 出场，后面只给它换皮，不换名字。

当前进度：课文 W01–W48 已齐。实现进度看根目录 `HANDOFF.md`。Cloud / Ubuntu 把课文里的 `uv run ...` 原样跑；没有 `python` 就用 `python3`。PowerShell 脚本用 `pwsh -File`。

| 今晚 | 文件 | 你要能感觉到 |
|---|---|---|
| W1 夜1 | [W01-01](W01-01-模块不是类文件.md) | `import pyforge` 印出版本 |
| W1 夜2 | [W01-02](W01-02-uv不是JAVA_HOME.md) | 系统 Python 和 uv 不是同一套包 |
| W2 夜1 | [W02-01](W02-01-DailySession与可变默认参数.md) | 两条 session 的 tags 互不影响 |
| W2 夜2 | [W02-02](W02-02-None和空是假.md) | `if items` 吞空列表被测试钉死 |
| W3 夜1 | [W03-01](W03-01-浅拷贝你能看见.md) | 改 B，A 一起变，终端能印出来 |
| W3 夜2 | [W03-02](W03-02-SliceRegistry.md) | 按 tag 过滤，复用 Session 里那份 tags |
| W4 夜1 | [W04-01](W04-01-异常链.md) | `__cause__` 不是空 |
| W4 夜2 | [W04-02](W04-02-frozen-dataclass.md) | 改字段会炸，才能当 dict key |
| W05 | [W05-01-JSON落盘](W05-01-JSON落盘.md) | pathlib 写出今晚的 DailySession |
| W06 | [W06-01-argparse不是Click](W06-01-argparse不是Click.md) | CLI session start|end|list |
| W07 | [W07-01-sqlite事务](W07-01-sqlite事务.md) | SqliteSessionRepository |
| W08 | [W08-01-生成器与原子写](W08-01-生成器与原子写.md) | export_sessions |
| W09 | [W09-01-Protocol双仓储](W09-01-Protocol双仓储.md) | SessionRepository Protocol |
| W10 | [W10-01-Generic与UoW](W10-01-Generic与UoW.md) | Repository[T] |
| W11 | [W11-01-mypy过类型](W11-01-mypy过类型.md) | domain+services 过 mypy |
| W12 | [W12-01-Pydantic拒脏数据](W12-01-Pydantic拒脏数据.md) | CourseManifest |
| W13 | [W13-01-GIL你能看见](W13-01-GIL你能看见.md) | bench gil |
| W14 | [W14-01-GateRunner子进程](W14-01-GateRunner子进程.md) | GateRunner 超时 |
| W15 | [W15-01-asyncio假HTTP](W15-01-asyncio假HTTP.md) | 假 HTTP 拉索引 |
| W16 | [W16-01-httpx取消](W16-01-httpx取消.md) | catalog fetch |
| W17 | [W17-01-schema幂等](W17-01-schema幂等.md) | schema_v1.sql |
| W18 | [W18-01-约束与回滚](W18-01-约束与回滚.md) | gate_attempts |
| W19 | [W19-01-csv整批事务](W19-01-csv整批事务.md) | import_learning_events |
| W20 | [W20-01-周报聚合](W20-01-周报聚合.md) | WeeklyReportService |
| W21 | [W21-01-Django对照Spring](W21-01-Django对照Spring.md) | /health/ ok |
| W22 | [W22-01-ORM对齐三张表](W22-01-ORM对齐三张表.md) | 三张表+迁移 |
| W23 | [W23-01-FBV与HTMX](W23-01-FBV与HTMX.md) | /weeks/ partial |
| W23 夜2 | [W23-02-weeks有血肉](W23-02-weeks有血肉.md) | 开工 / 收工 / 点 tag / 台账 |
| W24 | [W24-01-ModelForm与CSRF](W24-01-ModelForm与CSRF.md) | HTMX 建 session |
| W25 | [W25-01-pytest-django工厂](W25-01-pytest-django工厂.md) | factories.py |
| W26 | [W26-01-覆盖率门槛](W26-01-覆盖率门槛.md) | cov-fail-under=80 |
| W27 | [W27-01-CLI与Web对账](W27-01-CLI与Web对账.md) | parity |
| W28 | [W28-01-本地CI](W28-01-本地CI.md) | scripts/ci.ps1 |
| W29 | [W29-01-可编辑安装](W29-01-可编辑安装.md) | editable install |
| W30 | [W30-01-console-scripts](W30-01-console-scripts.md) | pyforge 命令 |
| W31 | [W31-01-wheel](W31-01-wheel.md) | 只装 wheel 能跑 |
| W32 | [W32-01-uv-lock-frozen](W32-01-uv-lock-frozen.md) | uv.lock --frozen |
| W33 | [W33-01-插件Protocol](W33-01-插件Protocol.md) | PluginRegistry |
| W34 | [W34-01-entry-points](W34-01-entry-points.md) | pyforge.plugins |
| W35 | [W35-01-钩子隔离](W35-01-钩子隔离.md) | 插件炸不影响提交 |
| W36 | [W36-01-番茄与错题](W36-01-番茄与错题.md) | 可 disable 的两个插件 |
| W37 | [W37-01-structlog请求号](W37-01-structlog请求号.md) | request_id |
| W38 | [W38-01-doctor](W38-01-doctor.md) | pyforge_doctor |
| W39 | [W39-01-metrics](W39-01-metrics.md) | /metrics/ |
| W40 | [W40-01-错误脱敏](W40-01-错误脱敏.md) | ErrorEvent 无密码 |
| W41 | [W41-01-YAML吃课表](W41-01-YAML吃课表.md) | import_course |
| W42 | [W42-01-门禁引擎](W42-01-门禁引擎.md) | GateRunner 写库 |
| W43 | [W43-01-HTMX筛选](W43-01-HTMX筛选.md) | 只刷新 tbody |
| W44 | [W44-01-备份恢复](W44-01-备份恢复.md) | restore 回点 |
| W45 | [W45-01-查询预算](W45-01-查询预算.md) | ≤8 queries |
| W46 | [W46-01-prod-doctor](W46-01-prod-doctor.md) | DEBUG=True 失败 |
| W47 | [W47-01-MkDocs与seed](W47-01-MkDocs与seed.md) | seed_demo |
| W48 | [W48-01-bootstrap](W48-01-bootstrap.md) | 空目录全绿 |

阅读节奏固定（为了丝滑，不要换格式）：

1. **今晚能感觉到什么**（先看结果）
2. **和 Java 不同的一句**
3. **示范**（先原样跑）
4. **反例**（必炸或必错）
5. **今晚只改这一刀**
6. **验收命令**
7. **下一课怎么接**
