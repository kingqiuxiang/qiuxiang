# W39-01 metrics

## 今晚能感觉到什么

打一次 `/weeks/` 再打 `/metrics/`，请求计数增加。不上 Grafana。

## 和 Java 不同的一句

Java：Micrometer。  
Django：进程内计数 + 文本页。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_metrics
```

## 反例（必错）

```text
接 SaaS APM 当作业——禁止
```

## 今晚只改这一刀

- `/metrics/`
- `test_metrics`

## 验收命令

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_metrics
```

## 下一课怎么接

有计数了。下一课错误事件脱敏：日志里不能有密码。这是 G10。
