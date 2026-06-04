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

## 第三方语言包

本仓库只收纳 **first-party** 语言包。第三方贡献者添加新语言仍走
[`aster-lang-template`](https://github.com/aster-cloud/aster-lang-template)
脚手架 + SPI 注册机制；本仓库不是第三方扩展点。

## 迁移状态

合并迁移仍在过渡期。原 `aster-lang-en` / `-zh` / `-de` 仓库在一个
deprecation release 周期内继续以原坐标发布 thin wrapper，之后归档。
切换 `aster-api` / `aster-deploy` 消费方的工作受 ADR 0011 的 gating
conditions 约束，单独排期。
