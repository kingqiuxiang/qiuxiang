# W38-01 doctor

## 今晚能感觉到什么

`manage.py pyforge_doctor` 打印 uv/db/DEBUG 几项，正常退出 0。不是 K8s probe。

## 和 Java 不同的一句

Java：`actuator/info`。  
Django：管理命令。

## 示范（先原样跑）

```powershell
uv run python src/forge_web/manage.py pyforge_doctor
```

## 反例（必错）

```text
做成 K8s liveness 玄学——禁止
```

## 今晚只改这一刀

- `pyforge_doctor` 命令

## 验收命令

```powershell
uv run python src/forge_web/manage.py pyforge_doctor
```

## 下一课怎么接

自检有了。下一课 `/metrics/` 吐最简计数。
