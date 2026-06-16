# aster-lang-locales

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

First-party Aster CNL 语言包的合并仓库。把原先各自独立的
`aster-lang-en` / `aster-lang-zh` / `aster-lang-de` 三个仓库收敛为
单一 Gradle multi-module 项目（见 ADR 0011）。

## 为什么合并

三个 first-party locale 包共享几乎相同的 `build.gradle.kts`、
`settings.gradle.kts` 与 SPI 注册布局，且从未独立于彼此发版——它们
总是随 core 的语法变更一起移动。独立仓库带来的是三倍的维护开销
（lock-step 编辑、`aster-api` 里三行 includeBuild、`aster-deploy` 里
三个 build:pack-* 任务），却没有换来任何独立发版收益。

合并后：一次 `./gradlew build` 构建全部三个包，版本图统一，维护毛刺消失。

## 模块

| 模块 | 发布坐标 | 内容 |
|---|---|---|
| `:en` | `cloud.aster-lang:aster-lang-en` | 英文词法表 + en-US backbone 同步校验 |
| `:zh` | `cloud.aster-lang:aster-lang-zh` | 简体中文词法表 + 7 个语法变换器 + CJK v2 一致性 |
| `:de` | `cloud.aster-lang:aster-lang-de` | 德语词法表 |

**坐标保持不变**：每个模块发布的 artifactId 与合并前的独立仓完全一致，
因此现有 Maven 消费者（`aster-api`、`aster-lang-core` 测试等）在过渡期
继续解析同一坐标。

## 构建

```bash
# 前置：aster-lang-core 已发布到 Maven Local
cd ../aster-lang-core && ./gradlew publishToMavenLocal -x test

# 构建 + 发布全部三个语言包到 Maven Local
cd ../aster-lang-locales && ./gradlew build publishToMavenLocal
```

`:en` 模块的 `verifyLexiconParity` 任务会校验 `en-US.json` 与
`aster-lang-core` 内嵌副本逐字节一致——需在两个仓库都已 checkout 的
工作区运行。

## 插件加载机制不受合并影响

合并改变的是语言包的**构建来源**，没有改变**加载机制**。`LexiconRegistry`
（在 `aster-lang-core`）发现语言包的单位始终是 **jar + 其内的
`META-INF/services/aster.core.lexicon.LexiconPlugin` + SPI ServiceLoader**，
与"语言包来自哪个 repo / 是否同 repo"无关。每个 module 仍各自产出
`aster-lang-{en,zh,de}.jar`，坐标不变、SPI 布局不变——对 ServiceLoader 而言
合并前后的 jar 无法区分。

两条加载路径都保留：

- **构建期 classpath 包**：消费方按需 `runtimeOnly 'cloud.aster-lang:aster-lang-<id>'`，
  启动时 `discoverPlugins()` 扫描 app classloader 注册。multi-module ≠ fat jar，
  三个 module 各发各的，可单独依赖。
- **运行时热插拔包**：`aster-api` 的 `HotPlugLexiconLoader` 监视 jar 目录，
  每个 jar 进独立 `URLClassLoader`，`discoverPlugins(thatLoader)` 注册，
  zero-restart。它不关心 jar 从哪个 repo 构建——本仓库 module 产物、
  过渡期老 repo 产物、第三方模板包都一样加载。

因此第三方包**无需被收编**即可被加载（自有部署放进 classpath 或热插拔目录皆可）；
收编只是把维护责任与官方 maven 发布权交给 Aster team。

## 第三方语言包

本仓库只收纳 **first-party（官方维护）** 语言包，**不是第三方扩展点**。
第三方添加新语言走
[`aster-lang-template`](https://github.com/aster-cloud/aster-lang-template)
脚手架 + SPI 注册机制——无需 Aster 介入即可自助开发并部署（SPI 在运行时
自动发现，见下文"插件加载机制不受合并影响"）。

第三方语言有两种归宿：

- **社区维护**：留在贡献者自己的 repo / maven 坐标，在
  [docs/community/lexicons](https://aster-lang.dev/community/lexicons) 登记。Aster 不维护、不背书。
- **官方背书 → 收编**：成熟的主流语种可申请被官方**收编**进本仓库，成为一个新 module
  （`cloud.aster-lang:aster-lang-<lang>`），由 Aster team 接管维护、安全审计与 maven 发布。
  准入流程见下。

## 官方收编（Adoption）准入流程

> 收编是**可选**的。一个语言包不被收编也能正常工作——SPI 机制不要求语言包住在本仓库。
> 收编换来的是 Aster team 接管维护 + 官方 maven 发布 + 安全审计背书。

收编一个第三方语言包为本仓库的新 module，须按序满足：

**1. 前置门槛（申请人自证）**
- 语言包基于 `aster-lang-template` 构建，`./gradlew validateLexicon` 与 `./gradlew test` 全绿
- lexicon 覆盖 `en-US.json` 的全部 keyword（一一对应，无缺失）
- SPI ABI 与当前 core 兼容（`LexiconAbiVersion` v1.x）
- `meta.id` 是合法 IETF BCP 47，且与本仓库已有 module 无冲突

**2. 申请**
- 在本仓库 [Issues](https://github.com/aster-cloud/aster-lang-locales/issues) 发起 Adoption 申请，
  附贡献者 fork 链接 + 上述自证截图
- Aster reviewer **24h** 内首次回复

**3. Aster 评审（收编方执行）**
- 安全审计：lexicon JSON 无 Aster 语法保留字符注入、无可执行内容
- 双引擎一致性：被收编语言的样本须能通过 tier1-parity 校验（与 en-US 输出相同 Core IR）
- 法务：贡献者签署 Apache 2.0 + 署名权确认

**4. 收编落库（Aster team 执行，非贡献者）**
- 在 `locales/<lang>/` 新建 module（结构对齐现有 `:en`/`:zh`/`:de`）
- `settings.gradle.kts` 增 `include(":<lang>")` + projectDir 映射
- 若该语言需要 canonicalizer 翻译目标，`build.gradle.kts` 加 `testRuntimeOnly(project(":en"))`
- 保留原贡献者署名（git history / NOTICE）
- 首次随本仓库统一版本发布（当前 `1.0.2` 线）

**5. 收编后**
- 原贡献者列入 [contributor 名录](https://aster-lang.dev/community/contributors)；
  符合条件者获 **Aster Language Steward** 标签（见 template README 的"贡献激励"）
- 该语言的后续维护责任转移给 Aster team；贡献者可继续以 co-maintainer 身份参与

> **退出**：被收编语言若长期失修或与 core 漂移不可调和，Aster team 可将其从本仓库
> 移出、降级回"社区维护"路径，并在 docs/community/lexicons 标注。

## 迁移状态

合并迁移仍在过渡期。原 `aster-lang-en` / `-zh` / `-de` 仓库在一个
deprecation release 周期内继续以原坐标发布 thin wrapper，之后归档。
切换 `aster-api` / `aster-deploy` 消费方的工作受 ADR 0011 的 gating
conditions 约束，单独排期。
