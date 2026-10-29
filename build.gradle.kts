// Root build for the consolidated first-party locale packs (ADR 0011).
//
// Each module under locales/<id> is a thin java-library that ships one
// SPI-registered lexicon pack. The build config is shared here via
// `subprojects {}` so the three modules don't each carry a near-identical
// build.gradle.kts (the duplication that motivated the consolidation).
//
// 发布坐标：每个模块发布 `cloud.aster-lang:aster-lang-locales-<id>`
// （见 locales/*/build.gradle.kts），**不是**独立仓时代的
// `cloud.aster-lang:aster-lang-<id>`——GitHub Packages 对已归档仓拥有的包名
// 返回 422，旧坐标无法在本仓复用，且已冻结在 1.0.2 线不再更新
// （详见 settings.gradle.kts 与 README「消费者注意」）。

// 共享版本目录句柄（aster-lang-platform，ADR 0012）。subprojects {} 块里
// 无法用 root 生成的 asterLibs.* type-safe 访问器，故通过
// VersionCatalogsExtension 显式查表，效果等价。
val asterLibs: VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("asterLibs")
val asterCore = asterLibs.findLibrary("core").get()

// Maven lexicon jar 版本 = 版本目录的 asterLang（JVM 生态单一版本源，ADR 0012）。
// **不**硬编码字面量：曾因 ui-messages npm 包独立 bump 误把 subprojects.version 也 bump
// （1.0.6），脱离 catalog asterLang(1.0.3)→消费方按 catalog 解析 aster-lang-locales-en:1.0.3
// 但本仓发的是孤儿 1.0.6→core CI parity 解析失败。从 catalog 取让 Maven jar 永远跟随
// 生态版本，杜绝此类 drift。npm @aster-cloud/ui-messages 仍走 ui-messages/package.json
// 的独立版本（与此无关）。
val asterLangVersion: String = asterLibs.findVersion("asterLang").get().requiredVersion

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "cloud.aster-lang"
    version = asterLangVersion

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
// 从 npm 包 ui-messages/package.json 读取版本，避免硬编码漂移（组 C 修复：
// 原硬编码 1.0.6 落后于实际发布版本，直接生成会把 manifest version 写回旧值）。
val uiMessagesPackageJson = rootProject.projectDir.resolve("ui-messages/package.json")
val uiMessagesVersion: String = run {
    @Suppress("UNCHECKED_CAST")
    val pkg = groovy.json.JsonSlurper().parse(uiMessagesPackageJson) as Map<String, Any>
    val v = pkg["version"] as? String
    require(!v.isNullOrBlank()) { "无法从 ${uiMessagesPackageJson} 读取非空 version" }
    v
}

val exportUiMessages by tasks.registering {
    group = "aster"
    description = "聚合各 locale 的 ui-messages 为单一 manifest 制品（ADR 0018 Phase 1）"

    // 输入：每个语言包模块（locales/<id>）resources 下的 ui-messages/*.json；
    // :test-support 等非语言包子项目没有界面文案，不参与聚合。
    val moduleMsgDirs = subprojects.filter { it.projectDir.parentFile.name == "locales" }.map {
        it.projectDir.resolve("src/main/resources/ui-messages")
    }
    val outDir = layout.buildDirectory.dir("ui-messages")

    moduleMsgDirs.forEach { inputs.dir(it).optional() }
    // version 来自 package.json → 声明为 input，bump 版本时才会重新导出（否则 up-to-date 留旧 manifest version）。
    inputs.file(uiMessagesPackageJson)
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

// ── 控制注册表校验与导出（ADR 0045 §2）──────────────────────────────────
// 输入可用 -PcontrolsSource 覆盖（负向测试用）。
val controlsSource: java.io.File = (findProperty("controlsSource") as String?)
    ?.let { rootProject.file(it) } ?: rootProject.file("controls/registry.json")

val exportControls by tasks.registering {
    group = "aster"
    description = "校验并导出控制注册表（ADR 0045）"
    inputs.file(controlsSource)
    val outDir = layout.buildDirectory.dir("controls")
    outputs.dir(outDir)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val reg = groovy.json.JsonSlurper().parse(controlsSource) as Map<String, Any?>
        val problems = validateControlsRegistry(reg)
        require(problems.isEmpty()) { "控制注册表不合法（${controlsSource}）：\n" + problems.joinToString("\n") }
        val out = outDir.get().asFile
        out.mkdirs()
        controlsSource.copyTo(out.resolve("registry.json"), overwrite = true)
        logger.lifecycle("controls registry ${reg["version"]} 已导出")
    }
}

val CONTROL_KEY = Regex("^[A-Z0-9_]+:[A-Z0-9_().]+$")
val PROFILE_ID = Regex("^[a-z][a-z0-9-]{0,63}$")
val EVIDENCE_SOURCES = setOf(
    "control", "outcome.pending", "outcome.requireApproval", "reviewers.any", "reviewers.guard.any",
    "reviewers.guard.approved", "reviewers.guard.rejected", "reviewers.guard.two", "whatIf.comparable", "rule.reason",
)
val LOCALES = listOf("en", "zh", "de")

/** 逐项检查注册表；返回问题列表（空 = 合法）。 */
fun validateControlsRegistry(reg: Map<String, Any?>): List<String> {
    val problems = mutableListOf<String>()
    if ((reg["version"] as? String).isNullOrBlank()) problems += "缺 version"
    @Suppress("UNCHECKED_CAST") val frameworks = reg["frameworks"] as? List<Map<String, Any?>> ?: emptyList()
    @Suppress("UNCHECKED_CAST") val controls = reg["controls"] as? List<Map<String, Any?>> ?: emptyList()
    @Suppress("UNCHECKED_CAST") val clauses = reg["clauses"] as? List<Map<String, Any?>> ?: emptyList()
    // 框架 id 必须非空：缺 id 的框架不进集合，从而引用它（或缺 framework）的控制点必然报错
    frameworks.filter { (it["id"] as? String).isNullOrBlank() }.forEach { problems += "框架缺 id: $it" }
    val frameworkIds = frameworks.mapNotNull { (it["id"] as? String)?.takeIf(String::isNotBlank) }.toSet()
    val keys = mutableSetOf<String>()
    for (c in controls) {
        val key = c["key"] as? String ?: "".also { problems += "控制点缺 key: $c" }
        if (!CONTROL_KEY.matches(key) || key.length > 64) problems += "控制键形态非法: $key"
        if (!keys.add(key)) problems += "控制键重复: $key"
        if (c["framework"] !in frameworkIds) problems += "控制键 $key 的 framework 未登记: ${c["framework"]}"
        problems += missingTitles("控制键 $key", c["title"])
    }
    // 同一控制点下条款 id 唯一（证据包与报告表以「控制点 + 条款」为行键）
    val clauseIds = mutableSetOf<Pair<Any?, String>>()
    for (cl in clauses) {
        val label = "条款 ${cl["clause"]}"
        val clauseId = cl["clause"] as? String
        if (clauseId.isNullOrBlank()) problems += "条款缺 clause id: $cl"
        else if (!clauseIds.add(cl["control"] to clauseId)) problems += "$label 在控制键 ${cl["control"]} 下重复"
        if (cl["control"] !in keys) problems += "$label 引用未登记控制键: ${cl["control"]}"
        problems += missingTitles(label, cl["title"])
        for (field in listOf("evidenced", "partial")) {
            @Suppress("UNCHECKED_CAST") val list = cl[field] as? List<String>
            if (list == null) { problems += "$label 缺 $field 数组"; continue }
            list.filter { it !in EVIDENCE_SOURCES }.forEach { problems += "$label 的 $field 含未知证据来源: $it" }
        }
    }
    // 档案：id 形态与唯一、三语标题、requires 三个字段齐全、frameworks 只引用已登记框架
    @Suppress("UNCHECKED_CAST") val profiles = reg["profiles"] as? List<Map<String, Any?>> ?: emptyList()
    val profileIds = mutableSetOf<String>()
    for (p in profiles) {
        val id = p["id"] as? String ?: ""
        profileIdProblem(p, profileIds)?.let { problems += it }
        problems += missingTitles("档案 $id", p["title"])
        problems += profileRequiresProblems(id, p["requires"], frameworkIds)
    }
    return problems
}

/** 档案 id 至多报一条问题：缺失、形态非法、重复三者互斥；合法且首次出现的 id 记入 seen。 */
fun profileIdProblem(p: Map<String, Any?>, seen: MutableSet<String>): String? {
    val id = p["id"] as? String ?: return "档案缺 id: $p"
    if (!PROFILE_ID.matches(id)) return "档案 id 形态非法: $id"
    return if (seen.add(id)) null else "档案 id 重复: $id"
}

/** 校验档案 requires：两个布尔开关齐全，frameworks 为数组且只引用已登记框架。 */
fun profileRequiresProblems(id: String, requires: Any?, frameworkIds: Set<String>): List<String> {
    @Suppress("UNCHECKED_CAST") val r = requires as? Map<String, Any?> ?: return listOf("档案 $id 缺 requires")
    val out = mutableListOf<String>()
    if (r["ruleId"] !is Boolean) out += "档案 $id 的 requires.ruleId 须为布尔"
    if (r["registeredControls"] !is Boolean) out += "档案 $id 的 requires.registeredControls 须为布尔"
    @Suppress("UNCHECKED_CAST") val fws = r["frameworks"] as? List<Any?>
    if (fws == null) return out + "档案 $id 的 requires.frameworks 须为数组"
    fws.filter { it !in frameworkIds }.forEach { out += "档案 $id 引用未登记框架: $it" }
    return out
}

fun missingTitles(label: String, title: Any?): List<String> {
    @Suppress("UNCHECKED_CAST") val t = title as? Map<String, Any?> ?: return listOf("$label 缺 title")
    return LOCALES.filter { (t[it] as? String).isNullOrBlank() }.map { "$label 缺 $it 标题" }
}

// 负向夹具：每个 bad-*.json 的校验问题须与 expected-problems.json 逐条一致，夹具集合与期望清单也须相同
val verifyControlsFixtures = tasks.register("verifyControlsFixtures") {
    group = "verification"
    description = "对全部负向控制注册表夹具运行校验并比对期望问题列表"
    val fixturesDir = rootProject.file("controls/src/test-fixtures")
    inputs.dir(fixturesDir)
    doLast {
        val slurper = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val expected = slurper.parse(fixturesDir.resolve("expected-problems.json")) as Map<String, List<String>>
        val fixtures = fixturesDir.listFiles()!!.map { it.name }
            .filter { it.startsWith("bad-") && it.endsWith(".json") }.sorted()
        val mismatches = mutableListOf<String>()
        if (fixtures.toSet() != expected.keys) {
            mismatches += "夹具与期望清单不一致：夹具 $fixtures，期望 ${expected.keys.sorted()}"
        }
        for (name in fixtures) {
            @Suppress("UNCHECKED_CAST")
            val actual = validateControlsRegistry(slurper.parse(fixturesDir.resolve(name)) as Map<String, Any?>)
            if (actual != expected[name]) mismatches += "$name：期望 ${expected[name]}，实际 $actual"
        }
        if (mismatches.isNotEmpty()) throw GradleException("负向夹具校验结果不符：\n" + mismatches.joinToString("\n"))
        logger.lifecycle("verifyControlsFixtures: ${fixtures.size} 个负向夹具的问题列表全部一致")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(exportControls, verifyControlsFixtures) }
}
