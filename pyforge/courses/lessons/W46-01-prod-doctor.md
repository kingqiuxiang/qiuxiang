# W46-01 prod doctor

## 今晚能感觉到什么

`pyforge_doctor --env prod` 在 `DEBUG=True` 时退出非 0。不是渗透课。

## 和 Java 不同的一句

Java：prod profile 禁止 devtools。  
Django：医生命令读 settings。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py pyforge_doctor --env prod
```

（本机 DEBUG=True 时期望失败；测试里分别断言两种环境。）

## 反例（必错）

```text
写 exploit / 渗透脚本——禁止
```

## 今晚只改这一刀

- doctor --env prod
- 测试覆盖 DEBUG

## 验收命令

```powershell
uv run python src/forge_web/manage.py pyforge_doctor --env prod
```

## 下一课怎么接

prod 门有了。下一课 MkDocs + `seed_demo`。
