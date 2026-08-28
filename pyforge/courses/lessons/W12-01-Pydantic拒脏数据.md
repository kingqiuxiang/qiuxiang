# W12-01 Pydantic v2 拒脏数据

## 今晚能感觉到什么

缺 `weeks` 或 `n` 不是 int，`CourseManifest.model_validate` 抛 `ValidationError`。合法 yaml 结构能过。

## 和 Java 不同的一句

Java：Bean Validation 注解。  
Python：Pydantic v2 `BaseModel`。不上 Ninja/DRF。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_course_manifest.py -q
```

这是 **G3**：mypy + `test_course_manifest` + `test_repo_protocol`。

## 反例（必错）

```python
def load(d):
    return d  # 脏数据静默进来
```

## 今晚只改这一刀

- `src/pyforge/domain/manifest.py`
- `tests/test_course_manifest.py`

禁止：Ninja/DRF。

## 验收命令

```powershell
uv run mypy src/pyforge/domain src/pyforge/services && uv run pytest tests/test_course_manifest.py tests/test_repo_protocol.py -q
```

## 下一课怎么接

数据壳硬了。下一课用纯 Python 看 GIL：CPU 密集两线程不会两倍快。
