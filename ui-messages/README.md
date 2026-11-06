# @aster-cloud/ui-messages

统一语言包的**界面文案 manifest**（ADR 0018，统一语言包 Phase 1）。

这是 `aster-lang-locales` 各 locale 模块 `src/main/resources/ui-messages/<locale-id>.json`
的**聚合产物**，走独立的 npm 发布通道——**不进 JVM jar**，避免触发全生态版本级联。

## 内容

`./gradlew exportUiMessages` 产出（`build/ui-messages/`，prepack 时拷入本目录）：

- `<locale-id>.json` — 每个 locale 的完整界面文案（38 namespace），如 `en-US.json`、`zh-CN.json`、`de-DE.json`
- `ui-messages-manifest.json` — envelope：

  ```json
  {
    "schema": "aster-ui-messages-manifest/v1",
    "version": "1.0.2",
    "locales": [
      { "id": "en-US", "file": "en-US.json", "sha256": "…", "bytes": 129457 }
    ]
  }
  ```

  `sha256` 既是完整性校验，也是 `aster-api` → Workers KV 的**版本化缓存 key 来源**
  （`messages:<locale>:v<sha 前 8 位>`）：文案一变 sha 变 → KV key 自然换 → 边缘自然刷新。

- `syntax-diagnostics/<locale-id>.json` — 语法错误文案目录（en-US / zh-CN / de-DE / hi-IN，见下节）
- `evaluation-diagnostics/<locale-id>.json` — 评估 400 诊断文案目录（en-US / zh-CN / de-DE / hi-IN，见下节）

## 语法错误文案目录（syntax-diagnostics）

解析错误没有错误码，进不了按错误码组织的 diagnostic overlay，因此单独成一个目录（aster-api#423）。
真相源是仓根 `syntax-diagnostics/<locale-id>.json`，两条通道分发同一份文件：

- **Maven**：打进 `cloud.aster-lang:aster-lang-locales-en` jar 的 `syntax-diagnostics/`，
  aster-api 的 `SyntaxMessages` 从 classpath 读取（ANTLR 解析错误）。四种语言都放在 en 包里，
  因为 hi 的 lexicon 在独立的 aster-lang-hi 仓，而 en 包是 aster-api 恒在的 backbone。
- **npm**：本包的 `syntax-diagnostics/`，aster-cloud 编辑器的 `parse-diagnostic-l10n.ts`
  按 key 取文案（aster-lang-ts 解析错误）。

约定：

- 占位符写作 `{name}`；每个 locale 的 key 集与每条模板的占位符集必须与 `en-US` 一致
  （`SyntaxDiagnosticsCatalogTest`，接入 `check`）。
- `en-US` 里编辑器用到的句子与名词（`unknownStatement`、`token.returnType` 等）**就是** aster-lang-ts
  解析器的英文原文，编辑器按 en 值反查 key。改 en 值等于改匹配规则，须同步 aster-lang-ts。
- `style.spaceQuotesAfterHan` 为 `"true"` 时，汉字与引号片段相邻处补一个空格（zh）。

## 评估诊断文案目录（evaluation-diagnostics）

aster-api evaluate 系列 400 诊断（`ENTRY_AMBIGUOUS` 入口歧义、`invalid_agent` 非法 agent，aster-api#445）的展示文案。
code 是稳定契约，message 按请求语言取本目录。位置、分发通道与约定都与 syntax-diagnostics 相同：
真相源是仓根 `evaluation-diagnostics/<locale-id>.json`，Maven 打进 `aster-lang-locales-en` jar 的
`evaluation-diagnostics/`（aster-api `EvaluationDiagnostics` 从 classpath 读取），npm 随本包分发；
key 集与占位符集须与 `en-US` 一致（`EvaluationDiagnosticsCatalogTest`，接入 `check`）。
`agent.<REASON>` 的后缀是 aster-api `AgentIdentity.InvalidAgentException.Reason` 的枚举名。

## 真相源（system of record）

文案的唯一真相源是 `locales/<lang>/src/main/resources/ui-messages/<id>.json`。
**改文案 = 改这里 → PR → 发版**。本目录的 `*.json` 是生成产物，已 gitignore。

- **aster-api**：启动时加载本 manifest 到内存，`/api/v1/messages?locale=xx` 直接吐内存。
- **aster-cloud**：构建期拉取本包作 ① 运行时 fetch 失败的 fallback ② 本地 dev 离线可用。

> `hi-IN` 的 ui-messages 在独立的 `aster-lang-hi` 仓，其 manifest 由该仓自行导出。

## 校验

`verifyUiMessagesParity`（root build，接入各模块 `check` → CI PR-blocking）：
每个 locale 的**顶层 namespace 键集**必须与 `en-US` backbone 一致，防结构层漂移。
（叶子键缺失由 cloud `deepMergeMessages` fallback 到 en 兜底。）
