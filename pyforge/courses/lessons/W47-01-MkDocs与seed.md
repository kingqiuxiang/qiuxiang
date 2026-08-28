# W47-01 MkDocs + seed

## 今晚能感觉到什么

`seed_demo` 灌几条演示 session。`docs/` 里有 MkDocs 能 build 的首页。不另开文档仓。

## 和 Java 不同的一句

Java：Spring RestDocs。  
Python：MkDocs 静态页 + 管理命令灌数。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py seed_demo
```

## 反例（必错）

```text
新建独立 docs 仓——禁止
```

## 今晚只改这一刀

- `seed_demo`
- `mkdocs.yml` + 一页

## 验收命令

```powershell
uv run python src/forge_web/manage.py seed_demo
```

## 下一课怎么接

演示能种。最后一课 bootstrap：空目录按脚本能到可跑。这是 G12。
