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
