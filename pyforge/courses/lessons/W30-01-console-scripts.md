# W30-01 console_scripts

## 今晚能感觉到什么

`uv run pyforge --help` 打印用法。不要 Docker 当打包。

## 和 Java 不同的一句

Java：`bin` 脚本或 Spring Boot fat jar。  
Python：`[project.scripts] pyforge = "pyforge.cli:main"`。

## 示范（先原样跑）

```powershell
uv run pyforge --help
```

## 反例（必错）

```text
写 Dockerfile 当“发布”——今晚禁止
```

## 今晚只改这一刀

- pyproject scripts
- `--help` 能跑

## 验收命令

```powershell
uv run pyforge --help
```

## 下一课怎么接

命令有了。下一课打 wheel，只装 wheel 也能 `pyforge --version`。
