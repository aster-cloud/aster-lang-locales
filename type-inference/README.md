# 推断命名规则（ADR 0056 §3.2）

源码没写类型的形参 / 字段，两引擎都按名字推断类型，再按约束修正。本目录是这套规则的**唯一真相源**：

| 文件 | 内容 |
|---|---|
| `naming-rules.json` | 规则表：`{"version", "default", "rules": [{"type", "priority", "match", "words" \| "strings"}]}`，数组顺序有意义 |
| `naming-rules.schema.json` | 规则表的 JSON Schema（2020-12） |
| `test-vectors.json` | 共用测试向量 `[{"name", "constraints", "expected"}]`，`constraints` 为 IR v2 形状（不带 origin） |
| `test-vectors.schema.json` | 测试向量的 JSON Schema |
| `infer.mjs` | 参考实现，只用于本目录的自洽测试 |
| `naming-rules.test.mjs` | 参考实现逐条跑测试向量，并检查规则覆盖（每条规则至少一个命中、一个近似但不命中的向量） |

## 算法

1. **分词**：名字只含 ASCII 字母、数字与 `_` 时，先按 `_` 切段（空段丢弃），每段反复取
   `[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|[0-9]+` 的最左匹配，所有词做 ASCII 小写；否则词列表为空。
2. **逐条判定**：`firstWord` / `lastWord` 看首词 / 末词，`anyWord` 看任一词，`lastWordMulti` 要求末词命中且词数 ≥ 2；
   `textPrefix` / `textSuffix` 把整个名字做 ASCII 小写后比较前缀 / 后缀。词列表为空时 `*Word*` 规则不命中。
3. **取胜**：命中规则中 `priority` 最大者胜，同优先级取数组中靠前者；都不命中取 `default`。
4. **与词法表无关**：全部规则对所有模块生效。
5. **约束修正**：有 `Range` 时，任一端点含 `.` 得 `Float`，否则 `Int`；没有 `Range` 而有 `Pattern` 得 `Text`。
   名字结果为 `Text` 且约束为数值时取约束，`Int` 遇 `Float` 约束取 `Float`，其余保持名字结果。与书写顺序无关。

## 修改规则的顺序

1. 改本目录的 `naming-rules.json`，并在 `test-vectors.json` 补上新行为的向量（命中与近似不命中各一条）。
2. 运行 `node --test 'type-inference/*.test.mjs'` 与 `./gradlew verifyTypeInferenceRules`。
3. 在同名分支组里把 `naming-rules.json` 逐字节复制到两处副本：
   - aster-lang-core `src/main/resources/type-inference/naming-rules.json`（`NamingRulesSiblingParityTest`）
   - aster-lang-ts `shared/type-inference-naming-rules.json`（`test/unit/parser/naming-rules-parity.test.ts`）

副本与源不等时两侧 parity 测试失败；CI 缺少同级 locales 检出即失败。副本随引擎编译进制品，运行时不读本仓，
`version` 只在格式变化时递增。测试向量不复制：两引擎的单测直接读同级 locales 检出中的 `test-vectors.json`。
