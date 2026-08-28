# W23-02 weeks 有血肉

## 今晚能感觉到什么

`/weeks/` 不再是两行白表单。能开工、收工、点 tag 筛选、删一条。同一套 FBV + HTMX，不是 React SPA。

## 和 Java 不同的一句

Java：再开一个前端仓。  
Django：模板 + 一小段 CSS/JS，按钮 `hx-post` 换 tbody。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_week_list forge_web.tests.test_session_lifecycle
```

## 反例（必错）

```text
新开 Vite + React 喂 /api/sessions——禁止
```

## 今晚只改这一刀

- `base.html` + `forge.css` 锻造台皮
- session start / stop / delete
- 收工走 `PluginRegistry`（番茄/错题打 tag）
- `/` 台账、`/slices/`、`/gates/`

禁止：独立 SPA、第三个插件、Celery。

## 验收命令

```powershell
uv run python src/forge_web/manage.py test forge_web.tests.test_week_list forge_web.tests.test_session_lifecycle forge_web.tests.test_board
```

## 下一课怎么接

台账能摸。之后只修红，不新开第二产品。
