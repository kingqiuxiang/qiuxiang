# W23-01 FBV 与 HTMX

## 今晚能感觉到什么

`/weeks/` 整页能开。带 `HX-Request` 时只回 tbody 那截。不是 JSON 喂 SPA。

## 和 Java 不同的一句

Java：Thymeleaf fragment。  
Django：同一 FBV，看请求头决定整页还是 partial。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_week_list
```

## 反例（必错）

```text
/api/weeks/ 返回 JSON 给 React——禁止
```

## 今晚只改这一刀

- `weeks` 视图 + 模板
- `test_week_list`

## 验收命令

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_week_list
```

## 下一课怎么接

列表能看。下一课 ModelForm + CSRF，HTMX 建一条 DailySession。
