# W11-01 mypy 过类型

## 今晚能感觉到什么

`uv run mypy src/pyforge/domain src/pyforge/services` 退出码 0。没有 `type: ignore`。

## 和 Java 不同的一句

Java：javac 是硬门。  
Python：mypy 是另请的门禁，默认不跑。今晚把它钉进命令。

## 示范（先原样跑）

把仓储、导出、JSON 落盘收到 `domain/` 与 `services/`。

```powershell
uv run mypy src/pyforge/domain src/pyforge/services
```

## 反例（必错）

```python
x = 1  # type: ignore
```

今晚出现 `type: ignore` = 本课作废。

## 今晚只改这一刀

- 目录归位
- mypy 绿

禁止：为了过关删类型。

## 验收命令

```powershell
uv run mypy src/pyforge/domain src/pyforge/services
```

## 下一课怎么接

静态类型有了。下一课用 Pydantic v2 拒脏的 `CourseManifest`。
