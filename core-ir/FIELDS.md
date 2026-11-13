# Core IR v2 字段清单

> 由 `./gradlew generateCoreIrFields` 从 `core-ir.v2.schema.json` 生成，请勿手改；`verifyCoreIrSchema` 检查本文件与 schema 同步。

对象类节点的每个属性都在 `required` 中，且 `additionalProperties: false`。「无值时」是该字段没有值时恒输出的内容（ADR 0055 #19），「—」表示该字段总有值。

## Int32

所有 JSON 整数的承载类型（ADR 0055 §3）：与 Java int / Integer 一致，落在 CanonicalJson 的 safe integer 范围内。

定义：integer [-2147483648, 2147483647]

## SafeInt

只用于 Timeout.milliseconds（Java long）：safe integer 范围，仍以 JSON number 承载。

定义：integer [-9007199254740991, 9007199254740991]

## CanonicalDecimal

规范十进制串（ADR 0055 §5）：无指数、无前导零、无尾随零，小数部分全零时省略小数点；不存在 "-0"。由 lowering 从源码字面量经任意精度十进制求出。

定义：string，pattern `^(0|-?[1-9][0-9]*(\.[0-9]*[1-9])?|-?0\.[0-9]*[1-9])$`

## BuiltinEffect

授权型具体效应名（小写）。pure、async 与未知名都不授权任何效应，lowering 不把它们写入 effects。

定义：`"io"` | `"cpu"`

## ReservedEffectName

保留的效应名：内建效应 io、cpu，以及不授权的 pure、async（core EffectChecker 中 async 是 pure 的废弃别名）。效应参数名按 ASCII 大小写不敏感比较不得落入此集合，由 validateCoreIrInvariants 检查（effect-param-shadows-builtin）；EffectVarName 的 pattern 与此集合同步。

定义：`"io"` | `"cpu"` | `"pure"` | `"async"`

## EffectVarName

Func.effects 中的效应变量名：任意大小写下都不是保留效应名。它是否已在 effectParams 中声明由不变式检查（undeclared-effect-var）。

定义：string，pattern `^(?!(?:[Ii][Oo]|[Cc][Pp][Uu]|[Pp][Uu][Rr][Ee]|[Aa][Ss][Yy][Nn][Cc])$).+$`

## Position

源码位置，行列号均从 1 开始（值域只按 Int32 约束）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `line` | `Int32` | 否 | — |
| `col` | `Int32` | 否 | — |

## Origin

源码范围（ADR 0055 #17）：每个带 kind 的节点都必带且非 null（含约束元素）。written / role 是 ADR 0051 的原写法与解析角色，未改写时为 null。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `file` | string \| null | 是 | `null` |
| `start` | `Position` | 否 | — |
| `end` | `Position` | 否 | — |
| `written` | string \| null | 是 | `null` |
| `role` | `"TYPE_DECL"` \| `"FIELD_DECL"` \| `"MEMBER_DECL"` \| `"FUNC_DECL"` \| `"TYPE"` \| `"FIELD_REF"` \| `"MEMBER_REF"` \| `"FUNC_REF"` \| `"VALUE_REF"` \| `"LOCAL"` \| `null` | 是 | `null` |

## Module

编译单元（根节点）。schemaVersion 是整数 2（ADR 0055 #1、§5）；name 在源码没有模块头时为 null；profile 是 ADR 0046 治理档案 id，未声明为 null。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Module"` | 否 | — |
| `schemaVersion` | const `2` | 否 | — |
| `name` | string \| null | 是 | `null` |
| `profile` | string \| null | 是 | `null` |
| `decls` | `Decl`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Decl

顶层声明。

定义：`Import` | `Data` | `Enum` | `Func`

## Import

导入（ADR 0055 #2）：path 为点分模块路径，alias 为绑定名（未写别名为 null），version 为钉住的模块版本（未钉住为 null）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Import"` | 否 | — |
| `path` | string | 否 | — |
| `alias` | string \| null | 是 | `null` |
| `version` | `Int32` \| null | 是 | `null` |
| `origin` | `Origin` | 否 | — |

## Data

数据类型声明。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Data"` | 否 | — |
| `name` | string | 否 | — |
| `fields` | `Field`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Field

Data 字段。constraints 是源码约束（ADR 0055 #4）；typeInferred 为 true 表示 type 只是推断出的占位（#9）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `name` | string | 否 | — |
| `type` | `Type` | 否 | — |
| `annotations` | `Annotation`[] | 否 | `[]` |
| `constraints` | `Constraint`[] | 否 | `[]` |
| `typeInferred` | boolean | 否 | `false` |

## Enum

枚举声明。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Enum"` | 否 | — |
| `name` | string | 否 | — |
| `variants` | string[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Func

函数 / 规则。typeParams 只列源码声明的类型参数（#10）；effects 只含授权型具体效应 io / cpu（小写）与已声明的效应变量，按首次声明顺序、不重复；pure、async 与未知名不写入（#5、#6）；inputRefs 是 v2 唯一可空的列表，null 表示未计算、[] 表示已计算且不读任何输入，合法状态由不变式检查（#12）。没有 piiLevel / piiCategories（#8）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Func"` | 否 | — |
| `name` | string | 否 | — |
| `typeParams` | string[] | 否 | `[]` |
| `params` | `Param`[] | 否 | `[]` |
| `ret` | `Type` | 否 | — |
| `retTypeInferred` | boolean | 否 | `false` |
| `annotations` | `Annotation`[] | 否 | `[]` |
| `retAnnotations` | `Annotation`[] | 否 | `[]` |
| `effects` | `BuiltinEffect` \| `EffectVarName`[] | 否 | `[]` |
| `effectParams` | string[] | 否 | `[]` |
| `effectCaps` | string[] | 否 | `[]` |
| `effectCapsExplicit` | boolean | 否 | `false` |
| `inputRefs` | string[] \| null | 是 | `null` |
| `body` | `Block` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Param

函数与 Lambda 的形参，字段与 Field 相同。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `name` | string | 否 | — |
| `type` | `Type` | 否 | — |
| `annotations` | `Annotation`[] | 否 | `[]` |
| `constraints` | `Constraint`[] | 否 | `[]` |
| `typeInferred` | boolean | 否 | `false` |

## Annotation

注解（ADR 0055 #3）：args 是有序数组，保持源码顺序，无参为 []。同一注解内参数名唯一由不变式检查。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `name` | string | 否 | — |
| `args` | `AnnotationArg`[] | 否 | `[]` |

## AnnotationArg

注解参数。位置参数按出现顺序命名为 $0、$1…（序号只在位置参数之间计数），具名参数用其名字；value 只能是 string / Int32 / boolean。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `name` | string，pattern `^(\$(0\|[1-9][0-9]*)\|[^$].*)$` | 否 | — |
| `value` | string \| boolean \| `Int32` | 否 | — |

## Constraint

字段 / 形参约束（ADR 0055 #4）。与其它带 kind 的节点一样必带 origin（#17）。

定义：`Required` | `Range` | `Pattern`

## Required

必填约束。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Required"` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Range

范围约束：min / max 是规范十进制串，至少一个非 null。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Range"` | 否 | — |
| `min` | `CanonicalDecimal` \| null | 是 | `null` |
| `max` | `CanonicalDecimal` \| null | 是 | `null` |
| `origin` | `Origin` | 否 | — |

## Pattern

正则约束（源码 matching "…"）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Pattern"` | 否 | — |
| `regexp` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## Type

类型表达式。所有类型节点都带 origin（ADR 0055 #17）。

定义：`TypeName` | `TypeVar` | `TypeApp` | `Result` | `Maybe` | `Option` | `List` | `Map` | `FuncType` | `PiiType`

## TypeName

具名类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"TypeName"` | 否 | — |
| `name` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## TypeVar

类型变量。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"TypeVar"` | 否 | — |
| `name` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## TypeApp

类型应用。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"TypeApp"` | 否 | — |
| `base` | string | 否 | — |
| `args` | `Type`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Result

结果类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Result"` | 否 | — |
| `ok` | `Type` | 否 | — |
| `err` | `Type` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Maybe

可空类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Maybe"` | 否 | — |
| `type` | `Type` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Option

选项类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Option"` | 否 | — |
| `type` | `Type` | 否 | — |
| `origin` | `Origin` | 否 | — |

## List

列表类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"List"` | 否 | — |
| `type` | `Type` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Map

映射类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Map"` | 否 | — |
| `key` | `Type` | 否 | — |
| `val` | `Type` | 否 | — |
| `origin` | `Origin` | 否 | — |

## FuncType

函数类型。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"FuncType"` | 否 | — |
| `params` | `Type`[] | 否 | `[]` |
| `ret` | `Type` | 否 | — |
| `origin` | `Origin` | 否 | — |

## PiiType

PII 类型包装。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"PiiType"` | 否 | — |
| `baseType` | `Type` | 否 | — |
| `sensitivity` | string | 否 | — |
| `category` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## Stmt

语句。

定义：`Let` | `Set` | `Return` | `If` | `Match` | `Scope` | `Block` | `Start` | `Wait` | `Workflow`

## Let

变量绑定。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Let"` | 否 | — |
| `name` | string | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Set

赋值。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Set"` | 否 | — |
| `name` | string | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Return

返回。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Return"` | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## If

条件语句：没有 else 分支时 elseBlock 为 null。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"If"` | 否 | — |
| `cond` | `Expr` | 否 | — |
| `thenBlock` | `Block` | 否 | — |
| `elseBlock` | `Block` \| null | 是 | `null` |
| `origin` | `Origin` | 否 | — |

## Match

模式匹配。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Match"` | 否 | — |
| `expr` | `Expr` | 否 | — |
| `cases` | `Case`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Case

匹配分支。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Case"` | 否 | — |
| `pattern` | `Pat` | 否 | — |
| `body` | `CaseBody` | 否 | — |
| `origin` | `Origin` | 否 | — |

## CaseBody

匹配分支体：Return 或 Block。

定义：`Return` | `Block`

## Scope

作用域。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Scope"` | 否 | — |
| `statements` | `Stmt`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Block

代码块。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Block"` | 否 | — |
| `statements` | `Stmt`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Start

启动异步任务。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Start"` | 否 | — |
| `name` | string | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Wait

等待异步任务。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Wait"` | 否 | — |
| `names` | string[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Workflow

工作流（kind 为小写 workflow）：未声明重试 / 超时时 retry / timeout 为 null。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"workflow"` | 否 | — |
| `steps` | `Step`[] | 否 | `[]` |
| `effectCaps` | string[] | 否 | `[]` |
| `retry` | `RetryPolicy` \| null | 是 | `null` |
| `timeout` | `Timeout` \| null | 是 | `null` |
| `origin` | `Origin` | 否 | — |

## Step

工作流步骤（kind 为小写 step）：没有补偿块时 compensate 为 null。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"step"` | 否 | — |
| `name` | string | 否 | — |
| `body` | `Block` | 否 | — |
| `compensate` | `Block` \| null | 是 | `null` |
| `dependencies` | string[] | 否 | `[]` |
| `effectCaps` | string[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## RetryPolicy

重试策略。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `maxAttempts` | `Int32` | 否 | — |
| `backoff` | `"exponential"` \| `"linear"` | 否 | — |

## Timeout

超时（毫秒）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `milliseconds` | `SafeInt` | 否 | — |

## Pat

匹配模式。

定义：`PatNull` | `PatCtor` | `PatName` | `PatInt`

## PatNull

null 模式。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"PatNull"` | 否 | — |
| `origin` | `Origin` | 否 | — |

## PatCtor

构造器 / 枚举变体模式（ADR 0055 #15、#16）：0 参变体为 args:[]；没有 names。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"PatCtor"` | 否 | — |
| `typeName` | string | 否 | — |
| `args` | `Pat`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## PatName

绑定模式，只用于绑定（#16）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"PatName"` | 否 | — |
| `name` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## PatInt

整数模式。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"PatInt"` | 否 | — |
| `value` | `Int32` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Expr

表达式。

定义：`Name` | `Bool` | `Int` | `Long` | `Double` | `Decimal` | `String` | `Null` | `Ok` | `Err` | `Some` | `None` | `Construct` | `Call` | `Lambda` | `Await` | `IfExpr` | `ListLit`

## Name

名称引用。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Name"` | 否 | — |
| `name` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## Bool

布尔字面量。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Bool"` | 否 | — |
| `value` | boolean | 否 | `false` |
| `origin` | `Origin` | 否 | — |

## Int

32 位整数字面量。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Int"` | 否 | — |
| `value` | `Int32` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Long

64 位整数字面量，以十进制字符串承载。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Long"` | 否 | — |
| `value` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## Double

浮点字面量（ADR 0055 #18）：规范十进制串，不经二进制浮点。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Double"` | 否 | — |
| `value` | `CanonicalDecimal` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Decimal

Decimal 字面量（ADR 0025）：canonical 十进制字符串。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Decimal"` | 否 | — |
| `value` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## String

字符串字面量。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"String"` | 否 | — |
| `value` | string | 否 | — |
| `origin` | `Origin` | 否 | — |

## Null

null 字面量。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Null"` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Ok

Result 成功分支。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Ok"` | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Err

Result 错误分支。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Err"` | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Some

Option 有值分支。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Some"` | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## None

Option 无值。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"None"` | 否 | — |
| `origin` | `Origin` | 否 | — |

## Construct

构造表达式。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Construct"` | 否 | — |
| `typeName` | string | 否 | — |
| `fields` | `FieldInit`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## FieldInit

Construct 的字段初始化。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `name` | string | 否 | — |
| `expr` | `Expr` | 否 | — |

## Call

调用。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Call"` | 否 | — |
| `target` | `Expr` | 否 | — |
| `args` | `Expr`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Lambda

Lambda（ADR 0055 #13、#14）：返回类型字段名为 ret；captures 恒输出。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Lambda"` | 否 | — |
| `params` | `Param`[] | 否 | `[]` |
| `ret` | `Type` | 否 | — |
| `body` | `Block` | 否 | — |
| `captures` | string[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |

## Await

等待异步结果。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"Await"` | 否 | — |
| `expr` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## IfExpr

表达式级条件（ADR 0019 G2b）。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"IfExpr"` | 否 | — |
| `cond` | `Expr` | 否 | — |
| `thenE` | `Expr` | 否 | — |
| `elseE` | `Expr` | 否 | — |
| `origin` | `Origin` | 否 | — |

## ListLit

列表字面量。

| 属性 | 类型 | 可空 | 无值时 |
|---|---|---|---|
| `kind` | const `"ListLit"` | 否 | — |
| `elements` | `Expr`[] | 否 | `[]` |
| `origin` | `Origin` | 否 | — |
