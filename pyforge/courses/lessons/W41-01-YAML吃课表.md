# W41-01 YAML DSL 吃本文件

## 今晚能感觉到什么

`manage.py import_course courses/curriculum.yaml` 把 48 周写进库。再跑一遍不炸（幂等）。不自造语言。

## 和 Java 不同的一句

Java：自己解析 YAML 或用 Jackson。  
Python：PyYAML + 已有 `CourseManifest`。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py import_course courses/curriculum.yaml
```

## 反例（必错）

```text
发明一套 .pyforge 语言——禁止
```

## 今晚只改这一刀

- `import_course` 命令
- 幂等导入

## 验收命令

```powershell
uv run python src/forge_web/manage.py import_course courses/curriculum.yaml
```

## 下一课怎么接

课表进库了。下一课门禁引擎：GateRunner 结果写 `gate_attempts`。
