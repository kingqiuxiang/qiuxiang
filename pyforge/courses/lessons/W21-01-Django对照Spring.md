# W21-01 Django 对照 Spring

## 今晚能感觉到什么

`uv run python src/forge_web/manage.py check` 退出 0。浏览器或测试打 `/health/` 看到 `ok`。

## 和 Java 不同的一句

Java：Spring Boot `actuator/health`。  
Django：一个 FBV + `HttpResponse`。不要独立 frontend，不要 React。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py check
```

## 反例（必错）

```text
新建 frontend/ 用 Vite——今晚作废
```

## 今晚只改这一刀

- `src/forge_web/` Django 工程
- `/health/`

禁止：独立 SPA。

## 验收命令

```powershell
uv run python src/forge_web/manage.py check
```

## 下一课怎么接

进程起来了。下一课 ORM 对齐 W17 三张表并做迁移。
