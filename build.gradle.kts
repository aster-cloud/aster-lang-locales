// Root build for the consolidated first-party locale packs (ADR 0011).
//
// Each module under locales/<id> is a thin java-library that ships one
// SPI-registered lexicon pack. The build config is shared here via
// `subprojects {}` so the three modules don't each carry a near-identical
// build.gradle.kts (the duplication that motivated the consolidation).
//
// Coordinates are preserved: every module publishes
// `cloud.aster-lang:aster-lang-<id>` exactly as the standalone repos did,
// so existing Maven consumers (aster-api, aster-lang-core tests, …) keep
// resolving the same artifact ids during the deprecation window.

// 共享版本目录句柄（aster-lang-platform，ADR 0012）。subprojects {} 块里
// 无法用 root 生成的 asterLibs.* type-safe 访问器，故通过
// VersionCatalogsExtension 显式查表，效果等价。
val asterLibs: VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("asterLibs")
val asterCore = asterLibs.findLibrary("core").get()

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "cloud.aster-lang"
    version = "1.0.6"

    repositories {
        mavenLocal()
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    dependencies {
        // The Aster compiler core — provides the lexicon/SPI interfaces
        // each pack implements. Version from the shared catalog.
        add("implementation", asterCore)
        add("testImplementation", "org.junit.jupiter:junit-jupiter:6.0.0")
        add("testImplementation", "org.assertj:assertj-core:3.27.3")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/aster-cloud/aster-lang-locales")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}

// ── ui-messages manifest 导出（ADR 0018，统一语言包 Phase 1）──────────────
//
// 把每个 locale 模块的 `ui-messages/<locale-id>.json`（界面文案，38 namespace）
// 聚合成**单一 manifest 制品**，走独立的 npm/JSON 发布通道——**不进 JVM jar**，
// 避免触发全生态版本级联（见 hindi-full-support 的级联踩坑）。
//
// 产物（build/ui-messages/）：
//   - <locale-id>.json            每个 locale 的完整 messages（原样拷贝）
//   - ui-messages-manifest.json   envelope：版本 + 各 locale 的 sha256 + 字节数
//
// manifest 的 sha256 既是完整性校验，也是 aster-api → Workers KV 的版本化缓存
// key 来源（messages:<locale>:v<sha 前 8 位>），后端文案一变 sha 变 → KV key
// 自然换 → 边缘自然刷新（ADR 0018 ③）。
//
// 注意：hi-IN 的 ui-messages 在独立的 aster-lang-hi 仓，其 manifest 由该仓
// 自行导出；本任务只聚合 locales 仓内的 en-US/zh-CN/de-DE。
val uiMessagesVersion = "1.0.6"  // 与语言包版本对齐，但走独立发布节奏

val exportUiMessages by tasks.registering {
    group = "aster"
    description = "聚合各 locale 的 ui-messages 为单一 manifest 制品（ADR 0018 Phase 1）"

    // 输入：每个模块 resources 下的 ui-messages/*.json
    val moduleMsgDirs = subprojects.map {
        it.projectDir.resolve("src/main/resources/ui-messages")
    }
    val outDir = layout.buildDirectory.dir("ui-messages")

    moduleMsgDirs.forEach { inputs.dir(it).optional() }
    outputs.dir(outDir)

    doLast {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val out = outDir.get().asFile
        out.mkdirs()

        // 收集 <locale-id> → 文件，按 id 排序保证 manifest 稳定
        val localeFiles = moduleMsgDirs
            .filter { it.isDirectory }
            .flatMap { it.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList() }
            .sortedBy { it.nameWithoutExtension }

        val entries = localeFiles.map { f ->
            val bytes = f.readBytes()
            md.reset()
            val sha = md.digest(bytes).joinToString("") { "%02x".format(it) }
            // 原样拷贝到产物目录（npm 包内容）
            f.copyTo(out.resolve(f.name), overwrite = true)
            mapOf(
                "id" to f.nameWithoutExtension,
                "file" to f.name,
                "sha256" to sha,
                "bytes" to bytes.size
            )
        }

        // 写 envelope manifest（手写 JSON，避免引入序列化依赖）
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val entriesJson = entries.joinToString(",\n") { e ->
            """    { "id": "${esc(e["id"].toString())}", "file": "${esc(e["file"].toString())}", """ +
                """"sha256": "${e["sha256"]}", "bytes": ${e["bytes"]} }"""
        }
        val manifest = """{
  "schema": "aster-ui-messages-manifest/v1",
  "version": "$uiMessagesVersion",
  "locales": [
$entriesJson
  ]
}
"""
        out.resolve("ui-messages-manifest.json").writeText(manifest)
        logger.lifecycle("exportUiMessages → ${out.absolutePath} (${entries.size} locales)")
    }
}

// 让每个子模块 build 顺带产出 manifest，CI/本地一条 `./gradlew build` 即得制品。
// （root project 无 `build` task——只有 java-library 子模块有。）
subprojects {
    tasks.matching { it.name == "build" }.configureEach { dependsOn(exportUiMessages) }
}

// ── ui-messages 命名空间 parity 校验（ADR 0018，统一语言包 Phase 1）─────────
//
// 镜像现有 verifyLexiconKeywordParity 的纪律：每个 locale 的 ui-messages
// **顶层 namespace 键集**必须与 en-US backbone 完全一致——翻译值各不同，但
// namespace 集必须跨所有 locale 相同，否则前端按 namespace 取文案时会静默漏键。
// （叶子键的缺失由 cloud 的 deepMergeMessages fallback 到 en 兜底，不在此强校；
// 此处守的是结构层 namespace 漂移，正是"统一语言包"要消除的痛点。）
val verifyUiMessagesParity by tasks.registering {
    group = "verification"
    description = "Ensure every locale's ui-messages namespace set matches en-US backbone"

    val backbone = file("locales/en/src/main/resources/ui-messages/en-US.json")
    val others = listOf(
        file("locales/zh/src/main/resources/ui-messages/zh-CN.json"),
        file("locales/de/src/main/resources/ui-messages/de-DE.json")
    )
    inputs.file(backbone)
    inputs.files(others)

    doLast {
        val parser = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val backboneNs = (parser.parse(backbone) as Map<String, Any>).keys

        val drift = StringBuilder()
        others.forEach { f ->
            @Suppress("UNCHECKED_CAST")
            val ns = (parser.parse(f) as Map<String, Any>).keys
            val missing = backboneNs - ns
            val extra = ns - backboneNs
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                drift.append("  ${f.name}: missing=$missing extra=$extra\n")
            }
        }
        if (drift.isNotEmpty()) {
            throw GradleException(
                "ui-messages namespace drift vs en-US backbone:\n$drift" +
                    "Sync the namespace set across all locale ui-messages."
            )
        }
        logger.lifecycle(
            "verifyUiMessagesParity: all locales match en-US backbone (${backboneNs.size} namespaces) ✓"
        )
    }
}

// parity 先于 manifest 导出（漂移则不产出污染制品）
exportUiMessages.configure { dependsOn(verifyUiMessagesParity) }

// 每个子模块 check 顺带跑 ui-messages parity → CI PR-blocking
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyUiMessagesParity) }
}
