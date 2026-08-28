# W01-02 uv 不是 JAVA_HOME

## 今晚能感觉到什么

你用系统 `python` 和 `uv run python` 各印一次 `sys.prefix`。两条路径不一样。这就是「环境」——比 JAVA_HOME 更狠：不进 venv，装再多包也是装到别人家里。

## 和 Java 不同的一句

Java：JDK 一套，`JAVA_HOME` 指向它，Maven 再隔离依赖。  
Python：**解释器本身就带着 site-packages**。没有「先选 JDK 再选仓库」这层习惯的话，`pip install` 会污染整机。`uv` 是这个仓库的解释器+锁，不是又一个 Maven。

## 示范（先原样跑）

上一课的包必须已经能 import。然后新建 `tests/test_env.py`：

```python
import sys


def test_running_inside_project_venv():
    prefix = sys.prefix.replace("\\", "/").lower()
    exe = sys.executable.replace("\\", "/").lower()
    assert ".venv" in prefix or ".venv" in exe
```

全程 `uv run`，不要激活 venv。

```bash
uv run pytest tests/test_env.py -q
uv run python -c "import sys; print('uv ', sys.prefix)"
python3 -c "import sys; print('sys', sys.prefix)"
```

Windows 本机把 `python3` 换成 `python` 即可。后两行路径应当不同。系统没有 `python` / `python3`、第二条失败，也算看懂——别去装全局 Python 凑这条。

## 反例（必错）

```bash
# 反例：用系统解释器跑测试（不要 activate）
python3 -m pytest tests/test_env.py -q
```

常见结果：`No module named pytest`。期望：只有 `uv run pytest` 稳定绿。

禁止用 `pip install django` 当反例——会污染系统 Python，今晚不装任何全局包。

## 今晚只改这一刀

- 新增 `tests/test_env.py`
- 必要时在 `HANDOFF.md` 记一句：系统 prefix vs uv prefix

禁止：安装 Django「先玩一下」、改 `__version__`、新建 CI。仓里已有 `.cursor/environment.json`，本课不要动它。

## 验收命令

```bash
uv run pytest tests/test_version.py tests/test_env.py -q
```

外加你自己看一眼两条 `sys.prefix` 不一样。

## 下一课怎么接

环境钉死了。下一课在**这个**解释器里写下第一个领域对象：`DailySession`。还是 `src/pyforge/`，不加新框架。
