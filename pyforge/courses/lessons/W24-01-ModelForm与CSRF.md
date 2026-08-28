# W24-01 ModelForm 与 CSRF

## 今晚能感觉到什么

带 CSRF 的 POST 能建 session。缺 CSRF 被拒。这是 **G6**。

## 和 Java 不同的一句

Java：Spring CSRF token。  
Django：`{% csrf_token %}` + `ModelForm`。不上 OAuth。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_session_form forge_web.tests.test_week_list
```

## 反例（必错）

```python
csrf_exempt  # 今晚出现 = 本课作废
```

## 今晚只改这一刀

- Session ModelForm
- `test_session_form`

禁止：OAuth。

## 验收命令

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_session_form forge_web.tests.test_week_list
```

## 下一课怎么接

表单绿了。下一课 pytest-django 工厂，不要 Playwright。
