# Core IR v2 schema

Core IR v2 的唯一真相源（ADR 0055 §3）。`core-ir.v2.schema.json` 是 JSON Schema 2020-12，`$id` 为 `https://aster-lang.cloud/schema/core-ir/v2`。两个引擎各持一份逐字节副本：aster-lang-core 在 `src/main/resources/core-ir/core-ir.v2.schema.json`，由 `CoreIrSchemaSiblingParityTest` 守护；aster-lang-ts 在 `shared/core-ir.v2.schema.json`，由 `test/unit/core/core-ir-schema-parity.test.ts` 守护。本仓不发布 schema 制品。

修改 IR 的步骤：先改本目录的 schema，运行 `./gradlew generateCoreIrFields` 重新生成 `FIELDS.md`，补齐夹具，`./gradlew verifyCoreIrSchema` 通过后，再把 schema 同步到 core 与 ts 的副本。`check` 依赖 `verifyCoreIrSchema`。

## 文件

| 文件 | 内容 |
|---|---|
| `core-ir.v2.schema.json` | schema 源文件 |
| `FIELDS.md` | 由 schema 生成的字段清单（每个 `$defs` 的属性、类型、可空性、无值时的输出），不要手改 |
| `test-fixtures/bad-*.json` | 负例：每个都是完整模块加一处违规 |
| `test-fixtures/ok-*.json` | 正例：必须通过 schema 与不变式；合起来覆盖 schema 中的每一种 `kind` |
| `test-fixtures/expected-problems.json` | 每个夹具的期望问题，`[{file, problems:[{path, code}]}]`，正例的 `problems` 为 `[]` |
| `test-fixtures/double-literals.json` | 源码浮点字面量到规范十进制串的对照，`[{source, canonical}]` |

## schema 约定

- 每个带 `kind` 的节点一个 `$defs`，名字等于 `kind`；只有 `workflow` / `step` 两个小写 kind 的 `$defs` 名为 `Workflow` / `Step`。不带 `kind` 的记录为 `Field`、`Param`、`Annotation`、`AnnotationArg`、`Origin`、`Position`、`FieldInit`、`RetryPolicy`、`Timeout`（`Case` 与 `Step` 保留现有的 `kind` 字段）。
- 联合类型的 `$defs` 名为 `Decl`、`Type`、`Stmt`、`Expr`、`Pat`、`Constraint`、`CaseBody`。约束节点 `{kind:"Pattern"}` 占用了 `Pattern` 这个名字，所以模式联合叫 `Pat`。联合先用 `enum` 限定 `kind`，再按 `kind` 用 `if` / `then` 分派到具体节点，所以校验错误会落在具体字段上，而不是笼统地报联合不匹配。
- 对象节点的全部属性都列入 `required`，并且 `additionalProperties: false`。
- 可空只出现在下列位置：`Module.name`（无模块头）、`Module.profile`、`Import.alias`、`Import.version`、`Origin.file` / `written` / `role`、`Range.min` / `max`（至少一个非 null）、`If.elseBlock`、`Workflow.retry` / `timeout`、`Step.compensate`，以及唯一可空的列表 `Func.inputRefs`。其余标量与对象都不可为 null，列表不可为 null。
- 所有 JSON 整数引用 `$defs/Int32`；只有 `Timeout.milliseconds` 用 `$defs/SafeInt`。
- `Double.value`、`Range.min` / `max` 引用 `$defs/CanonicalDecimal`，其 pattern 与 ADR 0055 §5 逐字相同。`Long.value`、`Decimal.value` 沿用 v1 的字符串，schema 不约束其格式。
- 每个带 `kind` 的节点都有非 null 的 `origin`（ADR 0055 #17、§6），约束元素 `Required` / `Range` / `Pattern` 也不例外：ADR #4 只列出约束的语义字段，origin 按 #17 统一补上。`Field`、`Param`、`Annotation`、`AnnotationArg`、`FieldInit`、`RetryPolicy`、`Timeout` 没有 `kind`，也不带 `origin`，与 Java `CoreModel` 现状一致。
- `Func.effects` 的元素要么属于 `$defs/BuiltinEffect`（`io`、`cpu`，小写），要么匹配 `$defs/EffectVarName`。后者拒绝任意大小写的保留名 `io`、`cpu`、`pure`、`async`。`pure`、`async` 与未知效应名都不授权任何效应（core `EffectChecker` 把它们视同 PURE），lowering 不把它们写入 `effects`，所以 `"pure"`、`"async"`、`"IO"` 都会被 schema 拒收。`effects` 按首次声明顺序排列、不重复。`effectParams` 的元素仍是普通字符串，与保留名的冲突由不变式检查，以便报出 E213 对应的问题。
- `AnnotationArg.name` 以 `$` 开头时只能是 `$0`、`$1`…，具名参数不能以 `$` 开头。
- `Origin.role` 取 ADR 0051 的十个角色名之一，或者为 null。

## 不变式

schema 表达不了的约束由两引擎各自的 `validateCoreIrInvariants` 检查，输入必须已经通过 schema。`verifyCoreIrSchema` 里有一份参考实现（`build.gradle.kts` 的 `coreIrInvariantProblems`），夹具的期望值就是由它核对的。`path` 是 JSON Pointer，问题列表按 `(path, code)` 排序后比较。

| code | 规则 | path |
|---|---|---|
| `dup-annotation-arg` | 同一个注解的 `args` 中参数名重复。检查树中所有 `annotations` 与 `retAnnotations`（Func、Field、Param，含 Lambda 形参） | 后出现的那个参数，例如 `/decls/1/fields/1/annotations/0/args/2` |
| `undeclared-effect-var` | `Func.effects` 的条目（schema 已保证它是 `io` / `cpu`，或不是保留名）既不是 `io` / `cpu`，也不等于该函数 `effectParams` 中的某个名字 | 该条目，例如 `/decls/4/effects/1` |
| `duplicate-effect` | `Func.effects` 中同一名字出现多次（lowering 按首次声明顺序去重） | 后出现的那个条目，例如 `/decls/3/effects/1` |
| `effect-param-shadows-builtin` | `Func.effectParams` 的条目按 ASCII 大小写不敏感比较等于某个保留效应名（`$defs/ReservedEffectName`：`io`、`cpu`、`pure`、`async`） | 该条目，例如 `/decls/4/effectParams/0` |
| `input-refs-state` | `Func.inputRefs` 的 null / 数组状态不合法（ADR 0055 #12）：模块 `profile` 非 null 且函数返回 Verdict 时必须是数组（可以为空），其余情况必须为 null | `/decls/<i>/inputRefs` |

「返回 Verdict」与 core `GovernanceChecker.returnsVerdict` 同义：`ret` 是 `{kind:"TypeName", name:"Verdict"}`；或者沿 `Block` / `Scope` 的语句、`If` 的两个分支、`Match` 各分支体可达的某个 `Return`，其 `expr` 是 `Call`，`target` 是 `Name`，名字为 `Verdict.allow`、`Verdict.deny`、`Verdict.require_approval`、`Verdict.escalate` 之一。

## 夹具的比较规则

- 期望 code 为 `schema` 的夹具只登记一个问题。各引擎只比较 code，因为不同校验器报告的路径不同；`path` 记录的是违规位置。`verifyCoreIrSchema` 另外要求 networknt 至少有一条拒收落在该 path 或其后代上，并且没有偏离这条祖先链的拒收，以保证夹具只有一处违规。
- 不变式类夹具必须通过 schema，然后比较 `{path, code}` 全等。
- `bad-missing-schema-version.json`、`bad-schema-version-1.json`、`bad-schema-version-string.json` 的 code 是 `schema`。读方在 schema 校验之前单独检查 `/schemaVersion`，对这三个夹具应报 `unsupported_schema_version`（ADR 0055 §5）。
- `bad-decimal-trailing-newline.json`（`"1.5\n"`）用来防止 `$` 在 Java 正则里匹配末尾换行之前的位置：校验器的 `pattern` 必须按 ECMA-262 语义执行，`$` 只能匹配输入结尾。networknt 2.x 会把 `$` 改写成输入结尾锚点，ajv 本来就是 ECMA 语义。
- JSON Schema 把 `2.0` 视为整数，schema 无法区分 `2` 与 `2.0`。Int32 字段的数值读方须按整数绑定。

## 诊断码

ADR 0055 为两种源码错误新增两个码（共享码表 `test-support/src/main/resources/diagnostics/error_codes.json`，真源是 aster-lang-ts `shared/error_codes.json`，core 有逐字节副本）：

| 码 | 名称 | 参数 | 阶段 | 说明 |
|---|---|---|---|---|
| E900 | `PARSE_DUPLICATE_ANNOTATION_ARG` | `annotation`、`name` | 解析 | 同一注解内参数名重复（ADR #3）。解析诊断原有码段 E050–E069 已用满，E070 起为 PII，E033–E049 留给类型检查（ADR 0051 §16.1），因此解析诊断的续段取空置的 E900–E999。句型 key 为 `syntax-diagnostics` 的 `duplicateAnnotationArg` |
| E213 | `EFFECT_PARAM_SHADOWS_BUILTIN` | `name`、`func` | 类型检查 | 效应参数与保留效应名（io、cpu、pure、async，大小写不敏感）同名（ADR #5），与 E210 / E211 同属效应变量一族 |

zh / de 译文在本仓 overlay，hi 的 overlay 在 aster-lang-hi。
