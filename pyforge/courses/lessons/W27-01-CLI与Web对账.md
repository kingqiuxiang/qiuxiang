# W27-01 CLI / Web 对账

## 今晚能感觉到什么

CLI 建的 session，Web 列表看得到；反过来也是。字段对齐。

## 和 Java 不同的一句

Java：API 契约测试。  
Python：同一 sqlite/ORM 背后的对账测试。不上 OpenAPI 生成。

## 示范（先原样跑）

```powershell
uv run pytest tests/test_parity_cli_web.py -q
```

## 反例（必错）

```text
CLI 写 JSON，Web 写另一张表，对不上还假装绿
```

## 今晚只改这一刀

- `tests/test_parity_cli_web.py`

## 验收命令

```powershell
uv run pytest tests/test_parity_cli_web.py -q
```

## 下一课怎么接

对账绿了。下一课本地 CI：`scripts/ci.ps1`。
