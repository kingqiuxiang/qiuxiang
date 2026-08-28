# W43-01 HTMX 筛选

## 今晚能感觉到什么

`/weeks/?tag=gil` 带 HX-Request 时响应是 tbody 片段，不是整页。不上 WebSocket。

## 和 Java 不同的一句

Java：HTMX 一样能用。  
Django：querystring 过滤 + 同一套 partial。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_week_filter
```

## 反例（必错）

```text
上 WebSocket 推筛选结果——禁止
```

## 今晚只改这一刀

- 过滤参数
- `test_week_filter`

## 验收命令

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_week_filter
```

## 下一课怎么接

筛选绿了。下一课备份恢复：restore 回到备份点。这是 G11 周边。
