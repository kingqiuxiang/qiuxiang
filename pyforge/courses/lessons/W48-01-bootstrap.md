# W48-01 bootstrap

## 今晚能感觉到什么

`pwsh -File scripts/bootstrap.ps1` 装依赖、migrate、seed，最后跑 `ci.ps1` 关键检查。不新开第二产品。

## 和 Java 不同的一句

Java：`./mvnw verify`。  
Python：一份 bootstrap 脚本。

## 示范（先原样跑）

```powershell
pwsh -File scripts/bootstrap.ps1
```

这是 **G12**。

## 反例（必错）

```text
bootstrap 里再 clone 一个新产品仓——禁止
```

## 今晚只改这一刀

- `scripts/bootstrap.ps1`

禁止：新开第二产品。

## 验收命令

```powershell
pwsh -File scripts/bootstrap.ps1
```

## 下一课怎么接

48 周闭环。之后只修红、还债，不加第二条产品线。
