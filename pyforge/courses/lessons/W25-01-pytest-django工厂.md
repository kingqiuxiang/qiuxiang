# W25-01 pytest-django 工厂

## 今晚能感觉到什么

`SessionFactory.create()` 一条就能进测试库。不要 Playwright。

## 和 Java 不同的一句

Java：Instancio / EasyRandom。  
Python：手写工厂函数即可，不必上 factory_boy 炫技。

## 示范（先原样跑）

```powershell
uv run pytest forge_web/tests/test_factories.py -q
```

## 反例（必错）

```text
为了“像生产”去开浏览器点按钮——今晚禁止
```

## 今晚只改这一刀

- `forge_web/tests/factories.py`
- `test_factories.py`

## 验收命令

```powershell
uv run pytest forge_web/tests/test_factories.py -q
```

## 下一课怎么接

工厂有了。下一课给 `src/pyforge` 加 80% 覆盖率门禁。
