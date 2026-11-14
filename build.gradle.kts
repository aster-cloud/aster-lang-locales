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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Core IR v2 schema 的元 schema 校验与夹具校验（ADR 0055 §3，见文件末尾 verifyCoreIrSchema）
        classpath("com.networknt:json-schema-validator:2.0.1")
    }
}

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
//   - syntax-diagnostics/<locale-id>.json  语法错误文案目录（仓根 syntax-diagnostics/，aster-api#423）
//   - evaluation-diagnostics/<locale-id>.json  评估 400 诊断文案目录（仓根 evaluation-diagnostics/，aster-api#445）
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
    // 语法错误文案目录（aster-api#423）：跨语言共用的单一目录，原样拷到产物的 syntax-diagnostics/ 下，
    // 供 aster-cloud 编辑器按 `@aster-cloud/ui-messages/syntax-diagnostics/<locale-id>.json` 引入。
    // 评估诊断文案目录（aster-api#445）同理，拷到产物的 evaluation-diagnostics/ 下。
    val catalogDirs = listOf("syntax-diagnostics", "evaluation-diagnostics").map { rootProject.file(it) }
    catalogDirs.forEach { inputs.dir(it) }
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

        catalogDirs.forEach { dir ->
            val catalogOut = out.resolve(dir.name)
            catalogOut.deleteRecursively()
            catalogOut.mkdirs()
            val files = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name } ?: emptyList()
            require(files.isNotEmpty()) { "${dir.name}/ 下没有文案文件：${dir}" }
            files.forEach { it.copyTo(catalogOut.resolve(it.name), overwrite = true) }
        }
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

/**
 * 校验档案 requires：两个布尔开关齐全，frameworks 为数组且只引用已登记框架。
 * verifiedInputs（ADR 0046 §11 D1，registry 1.2.0 起）可缺省（缺省即 false，兼容旧形态注册表），出现时须为布尔。
 */
fun profileRequiresProblems(id: String, requires: Any?, frameworkIds: Set<String>): List<String> {
    @Suppress("UNCHECKED_CAST") val r = requires as? Map<String, Any?> ?: return listOf("档案 $id 缺 requires")
    val out = mutableListOf<String>()
    if (r["ruleId"] !is Boolean) out += "档案 $id 的 requires.ruleId 须为布尔"
    if (r["registeredControls"] !is Boolean) out += "档案 $id 的 requires.registeredControls 须为布尔"
    if (r.containsKey("verifiedInputs") && r["verifiedInputs"] !is Boolean) out += "档案 $id 的 requires.verifiedInputs 须为布尔"
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

// ── Core IR v2 schema 校验（ADR 0055 §3）────────────────────────────────────
// core-ir/core-ir.v2.schema.json 是两引擎 Core IR 的唯一真相源；core 与 aster-lang-ts 各持逐字节副本。
// verifyCoreIrSchema 依次检查：schema 符合 2020-12 元 schema、内部 $ref 全部可解析；test-fixtures 中
// 期望 code 为 schema 的负例被拒收且拒收位置落在登记的 path 上，其余夹具通过 schema 且不变式问题与
// expected-problems.json 逐条一致；正例合起来覆盖 schema 中的每一种 kind；double-literals.json 的
// 对照与 BigDecimal 规范化结果一致；FIELDS.md 与 schema 同步。不变式的参考实现在下方，
// 与 core / ts 的 validateCoreIrInvariants 语义相同（README「不变式」）。
val coreIrDir = rootProject.file("core-ir")
val coreIrSchemaFile = coreIrDir.resolve("core-ir.v2.schema.json")
val coreIrFixturesDir = coreIrDir.resolve("test-fixtures")
val coreIrFieldsFile = coreIrDir.resolve("FIELDS.md")
val coreIrJson = ObjectMapper()

data class CoreIrProblem(val path: String, val code: String)

val VERDICT_CALLS = setOf("Verdict.allow", "Verdict.deny", "Verdict.require_approval", "Verdict.escalate")

/** 按 ASCII 大小写不敏感比较所用的小写化（内建效应名都是 ASCII）。 */
fun asciiLower(s: String): String = buildString { s.forEach { append(if (it in 'A'..'Z') it + 32 else it) } }

/** 与 core GovernanceChecker.returnsVerdict 同义：声明返回 Verdict，或任一可达 Return 直接调用 Verdict.*。 */
fun coreIrReturnsVerdict(func: JsonNode): Boolean {
    val ret = func.path("ret")
    if (ret.path("kind").asText() == "TypeName" && ret.path("name").asText() == "Verdict") return true
    return coreIrAnyReturnIsVerdictCall(func.path("body"))
}

fun coreIrAnyReturnIsVerdictCall(stmt: JsonNode): Boolean = when (stmt.path("kind").asText()) {
    "Return" -> stmt.path("expr").let {
        it.path("kind").asText() == "Call" && it.path("target").path("kind").asText() == "Name" &&
            it.path("target").path("name").asText() in VERDICT_CALLS
    }
    "Block", "Scope" -> stmt.path("statements").any { coreIrAnyReturnIsVerdictCall(it) }
    "If" -> coreIrAnyReturnIsVerdictCall(stmt.path("thenBlock")) || coreIrAnyReturnIsVerdictCall(stmt.path("elseBlock"))
    "Match" -> stmt.path("cases").any { coreIrAnyReturnIsVerdictCall(it.path("body")) }
    else -> false
}

/** 同一注解内参数名重复：报后出现的那个参数（树中任何位置的 annotations / retAnnotations）。 */
fun coreIrDuplicateArgs(node: JsonNode, path: String, out: MutableList<CoreIrProblem>) {
    if (node.isArray) node.forEachIndexed { i, child -> coreIrDuplicateArgs(child, "$path/$i", out) }
    if (!node.isObject) return
    node.fields().forEach { (key, child) ->
        if ((key == "annotations" || key == "retAnnotations") && child.isArray) {
            child.forEachIndexed { j, ann ->
                val seen = mutableSetOf<String>()
                ann.path("args").forEachIndexed { k, arg ->
                    if (!seen.add(arg.path("name").asText())) out += CoreIrProblem("$path/$key/$j/args/$k", "dup-annotation-arg")
                }
            }
        }
        coreIrDuplicateArgs(child, "$path/$key", out)
    }
}

/** 效应名集合：builtins 为授权型具体效应（io、cpu），reserved 另含不授权的 pure、async，均取自 schema。 */
data class CoreIrEffectNames(val builtins: Set<String>, val reserved: Set<String>)

fun coreIrFuncProblems(func: JsonNode, path: String, hasProfile: Boolean, names: CoreIrEffectNames): List<CoreIrProblem> {
    val out = mutableListOf<CoreIrProblem>()
    val effectParams = func.path("effectParams").map { it.asText() }
    effectParams.forEachIndexed { k, p ->
        if (asciiLower(p) in names.reserved) out += CoreIrProblem("$path/effectParams/$k", "effect-param-shadows-builtin")
    }
    // effects 按首次声明顺序去重：重复条目报后出现的那个；非 io / cpu 的条目必须是已声明的效应变量
    val seenEffects = mutableSetOf<String>()
    func.path("effects").forEachIndexed { k, e ->
        val name = e.asText()
        if (!seenEffects.add(name)) out += CoreIrProblem("$path/effects/$k", "duplicate-effect")
        else if (name !in effectParams && name !in names.builtins) out += CoreIrProblem("$path/effects/$k", "undeclared-effect-var")
    }
    // ADR 0055 #12：档案模块中返回 Verdict 的规则必须是数组，其余函数必须为 null
    if (func.path("inputRefs").isArray != (hasProfile && coreIrReturnsVerdict(func))) {
        out += CoreIrProblem("$path/inputRefs", "input-refs-state")
    }
    return out
}

/** validateCoreIrInvariants 的参考实现：输入须已通过 schema；结果按 (path, code) 排序。 */
fun coreIrInvariantProblems(module: JsonNode, names: CoreIrEffectNames): List<CoreIrProblem> {
    val out = mutableListOf<CoreIrProblem>()
    coreIrDuplicateArgs(module, "", out)
    val hasProfile = !module.path("profile").isNull
    module.path("decls").forEachIndexed { i, decl ->
        if (decl.path("kind").asText() == "Func") out += coreIrFuncProblems(decl, "/decls/$i", hasProfile, names)
    }
    return out.sortedWith(compareBy({ it.path }, { it.code }))
}

/** 指针 a 与 b 在同一条祖先链上（a 是 b 的祖先、后代或相同）。 */
fun onPointerLine(a: String, b: String): Boolean = a == b || b.startsWith("$a/") || a.startsWith("$b/")

// ---------- FIELDS.md 渲染 ----------
/** 表格单元格内的竖线须转义（GFM 表格在代码段内同样识别 \\|）。 */
fun mdEscape(s: String): String = s.replace("|", "\\|")

fun coreIrRenderType(s: JsonNode): String {
    s.get("\$ref")?.let { return "`" + it.asText().substringAfterLast('/') + "`" }
    s.get("const")?.let { return "const `" + it.toString() + "`" }
    s.get("enum")?.let { e -> return e.joinToString(" | ") { "`" + it.toString() + "`" } }
    (s.get("oneOf") ?: s.get("anyOf"))?.let { alts -> return alts.joinToString(" | ") { coreIrRenderType(it) } }
    s.get("allOf")?.let { arms -> return arms.joinToString(" | ") { coreIrRenderType(it.path("then")) } }
    val types = s.path("type").let { t -> if (t.isArray) t.map { it.asText() } else listOf(t.asText()) }
    return types.joinToString(" | ") { t ->
        when (t) {
            "array" -> coreIrRenderType(s.path("items")) + "[]"
            "integer" -> if (s.has("minimum")) "integer [${s["minimum"]}, ${s["maximum"]}]" else "integer"
            "string" -> if (s.has("pattern")) "string，pattern `" + s["pattern"].asText() + "`" else "string"
            else -> t
        }
    }
}

fun coreIrNullable(s: JsonNode): Boolean =
    s.path("type").let { t -> t.isArray && t.any { it.asText() == "null" } } ||
        (s.get("oneOf") ?: s.get("anyOf"))?.any { it.path("type").asText() == "null" } == true ||
        s.get("enum")?.any { it.isNull } == true

fun coreIrEmptyValue(s: JsonNode): String = when {
    coreIrNullable(s) -> "`null`"
    s.path("type").asText() == "array" -> "`[]`"
    s.path("type").asText() == "boolean" -> "`false`"
    else -> "—"
}

fun renderCoreIrFields(schema: JsonNode): String = buildString {
    append("# Core IR v2 字段清单\n\n")
    append("> 由 `./gradlew generateCoreIrFields` 从 `core-ir.v2.schema.json` 生成，请勿手改；")
    append("`verifyCoreIrSchema` 检查本文件与 schema 同步。\n\n")
    append("对象类节点的每个属性都在 `required` 中，且 `additionalProperties: false`。")
    append("「无值时」是该字段没有值时恒输出的内容（ADR 0055 #19），「—」表示该字段总有值。\n")
    schema.path("\$defs").fields().forEach { (name, def) ->
        append("\n## ").append(name).append("\n\n")
        def.get("description")?.let { append(it.asText()).append("\n\n") }
        val props = def.get("properties")
        if (def.path("type").asText() == "object" && props != null && !def.has("allOf")) {
            append("| 属性 | 类型 | 可空 | 无值时 |\n|---|---|---|---|\n")
            props.fields().forEach { (p, ps) ->
                append("| `").append(p).append("` | ").append(mdEscape(coreIrRenderType(ps))).append(" | ")
                append(if (coreIrNullable(ps)) "是" else "否").append(" | ").append(coreIrEmptyValue(ps)).append(" |\n")
            }
        } else {
            append("定义：").append(coreIrRenderType(def)).append("\n")
        }
    }
}

fun loadCoreIrSchema(): Pair<JsonNode, com.networknt.schema.Schema> {
    if (!coreIrSchemaFile.isFile) throw GradleException("缺少 Core IR schema：$coreIrSchemaFile")
    val node = coreIrJson.readTree(coreIrSchemaFile)
    val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
    val metaErrors = registry.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema")).validate(node)
    if (metaErrors.isNotEmpty()) {
        throw GradleException("core-ir schema 不符合 JSON Schema 2020-12 元 schema：\n" + metaErrors.joinToString("\n"))
    }
    val defs = node.path("\$defs")
    val dangling = mutableListOf<String>()
    fun walk(n: JsonNode) {
        if (n.isObject) n.fields().forEach { (k, v) ->
            if (k == "\$ref" && !defs.has(v.asText().removePrefix("#/\$defs/"))) dangling += v.asText()
            walk(v)
        }
        if (n.isArray) n.forEach { walk(it) }
    }
    walk(node)
    if (dangling.isNotEmpty()) throw GradleException("core-ir schema 中有无法解析的 \$ref：$dangling")
    return node to registry.getSchema(SchemaLocation.of(node.path("\$id").asText()), node)
}

fun collectKinds(n: JsonNode, out: MutableSet<String>) {
    if (n.isObject) {
        n.get("kind")?.takeIf { it.isTextual }?.let { out += it.asText() }
        n.forEach { collectKinds(it, out) }
    }
    if (n.isArray) n.forEach { collectKinds(it, out) }
}

val generateCoreIrFields = tasks.register("generateCoreIrFields") {
    group = "aster"
    description = "由 Core IR v2 schema 生成 core-ir/FIELDS.md（ADR 0055 §3）"
    inputs.file(coreIrSchemaFile)
    outputs.file(coreIrFieldsFile)
    doLast {
        coreIrFieldsFile.writeText(renderCoreIrFields(coreIrJson.readTree(coreIrSchemaFile)))
        logger.lifecycle("已生成 $coreIrFieldsFile")
    }
}

val verifyCoreIrSchema = tasks.register("verifyCoreIrSchema") {
    group = "verification"
    description = "校验 Core IR v2 schema、负向 / 正向夹具、浮点字面量对照与 FIELDS.md（ADR 0055 §3）"
    inputs.dir(coreIrDir)
    doLast {
        val (schemaNode, schema) = loadCoreIrSchema()
        val defs = schemaNode.path("\$defs")
        val names = CoreIrEffectNames(
            defs.path("BuiltinEffect").path("enum").map { it.asText() }.toSet(),
            defs.path("ReservedEffectName").path("enum").map { it.asText() }.toSet(),
        )
        // EffectVarName 的 pattern 须恰好排除 ReservedEffectName（任意大小写），两处不能各说各话
        val varName = Regex(defs.path("EffectVarName").path("pattern").asText())
        val leaked = names.reserved.flatMap { listOf(it, it.uppercase(), it.replaceFirstChar(Char::uppercaseChar)) }
            .filter { varName.matches(it) }
        if (leaked.isNotEmpty() || !varName.matches("E") || !names.reserved.containsAll(names.builtins)) {
            throw GradleException("EffectVarName / ReservedEffectName / BuiltinEffect 不一致：$leaked")
        }
        val mismatches = mutableListOf<String>()
        val expected = coreIrJson.readTree(coreIrFixturesDir.resolve("expected-problems.json"))
        val listed = expected.map { it.path("file").asText() }
        val onDisk = coreIrFixturesDir.listFiles()!!.map { it.name }
            .filter { (it.startsWith("bad-") || it.startsWith("ok-")) && it.endsWith(".json") }.sorted()
        if (listed.sorted() != onDisk) mismatches += "夹具与期望清单不一致：夹具 $onDisk，期望 ${listed.sorted()}"
        val seenKinds = mutableSetOf<String>()
        var negatives = 0
        var positives = 0
        for (entry in expected) {
            val name = entry.path("file").asText()
            val file = coreIrFixturesDir.resolve(name)
            if (!file.isFile) continue
            val doc = coreIrJson.readTree(file)
            val want = entry.path("problems").map { CoreIrProblem(it.path("path").asText(), it.path("code").asText()) }
            val errors = schema.validate(doc)
                .map { e -> e.instanceLocation.toString() + (e.property?.let { "/$it" } ?: "") to e.toString() }
            if (name.startsWith("ok-")) positives++ else negatives++
            if (name.startsWith("ok-") != want.isEmpty()) mismatches += "$name：ok- 夹具必须无问题，bad- 夹具必须至少登记一个问题"
            if (want.any { it.code == "schema" }) {
                if (want.size != 1) { mismatches += "$name：schema 类夹具只能登记一个问题"; continue }
                val at = want.single().path
                when {
                    errors.isEmpty() -> mismatches += "$name：期望被 schema 拒收，实际通过"
                    errors.none { (loc, _) -> loc == at || loc.startsWith("$at/") } ->
                        mismatches += "$name：没有落在 $at 上的拒收，实际 ${errors.map { it.second }}"
                    errors.any { (loc, _) -> !onPointerLine(loc, at) } ->
                        mismatches += "$name：存在与 $at 无关的拒收（夹具不止一处违规）：${errors.map { it.second }}"
                }
                continue
            }
            if (errors.isNotEmpty()) { mismatches += "$name：期望通过 schema，实际 ${errors.map { it.second }}"; continue }
            if (name.startsWith("ok-")) collectKinds(doc, seenKinds)
            val actual = coreIrInvariantProblems(doc, names)
            val wanted = want.sortedWith(compareBy({ it.path }, { it.code }))
            if (actual != wanted) mismatches += "$name：期望 $wanted，实际 $actual"
        }
        val schemaKinds = schemaNode.path("\$defs").mapNotNull { it.path("properties").path("kind").get("const")?.asText() }
        val uncovered = schemaKinds.filter { it !in seenKinds }
        if (uncovered.isNotEmpty()) mismatches += "正例没有覆盖这些 kind：$uncovered"
        // 浮点字面量对照：canonical 须匹配规范十进制语法，且等于 BigDecimal 精确求值、去尾随零后的无指数形式
        val decimal = Regex(schemaNode.path("\$defs").path("CanonicalDecimal").path("pattern").asText())
        val literals = coreIrJson.readTree(coreIrFixturesDir.resolve("double-literals.json"))
        for (row in literals) {
            val source = row.path("source").asText()
            val canonical = row.path("canonical").asText()
            val computed = java.math.BigDecimal(source).stripTrailingZeros().toPlainString()
            if (!decimal.matches(canonical)) mismatches += "double-literals：$canonical 不符合规范十进制语法"
            if (computed != canonical) mismatches += "double-literals：$source 应为 $computed，登记为 $canonical"
        }
        if (!coreIrFieldsFile.isFile || coreIrFieldsFile.readText() != renderCoreIrFields(schemaNode)) {
            mismatches += "core-ir/FIELDS.md 与 schema 不同步，运行 ./gradlew generateCoreIrFields 后提交"
        }
        if (mismatches.isNotEmpty()) throw GradleException("Core IR schema 校验失败：\n" + mismatches.joinToString("\n"))
        logger.lifecycle(
            "verifyCoreIrSchema: $negatives 个负向夹具的问题列表全部一致，$positives 个正例通过，" +
                "${literals.size()} 条浮点字面量对照一致，FIELDS.md 已同步"
        )
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyCoreIrSchema) }
}

// ── 推断命名规则（ADR 0056 §3.2）──────────────────────────────────────────────────────────────
// type-inference/naming-rules.json 是两引擎推断形参 / 字段类型的唯一真相源，core 与 aster-lang-ts 各持一份
// 逐字节副本（sibling parity 测试守护）。verifyTypeInferenceRules 检查两份 schema 符合 2020-12 元 schema，
// 规则表与共用测试向量各自通过 schema，且规则表满足 schema 表达不了的约束；向量与算法的自洽由
// node --test type-inference/*.test.mjs（参考实现 infer.mjs）负责。
val typeInferenceDir = rootProject.file("type-inference")

fun loadTypeInferenceSchema(name: String): com.networknt.schema.Schema {
    val file = typeInferenceDir.resolve(name)
    if (!file.isFile) throw GradleException("缺少 schema：$file")
    val node = coreIrJson.readTree(file)
    val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
    val metaErrors = registry.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema")).validate(node)
    if (metaErrors.isNotEmpty()) throw GradleException("$name 不符合 JSON Schema 2020-12 元 schema：\n" + metaErrors.joinToString("\n"))
    return registry.getSchema(SchemaLocation.of(node.path("\$id").asText()), node)
}

val verifyTypeInferenceRules = tasks.register("verifyTypeInferenceRules") {
    group = "verification"
    description = "校验推断命名规则表与共用测试向量（ADR 0056 §3.2）"
    inputs.dir(typeInferenceDir)
    doLast {
        val problems = mutableListOf<String>()
        val rules = coreIrJson.readTree(typeInferenceDir.resolve("naming-rules.json"))
        val vectors = coreIrJson.readTree(typeInferenceDir.resolve("test-vectors.json"))
        loadTypeInferenceSchema("naming-rules.schema.json").validate(rules).forEach { problems += "naming-rules.json：$it" }
        loadTypeInferenceSchema("test-vectors.schema.json").validate(vectors).forEach { problems += "test-vectors.json：$it" }
        // 同一个词 / 串出现在两条规则里时，胜负取决于优先级与数组顺序，容易被误改；除布尔前缀与末词
        // （active / valid 在首词与末词两条规则里各有一份）外不允许重复
        val owners = mutableMapOf<Pair<String, String>, Int>()
        rules.path("rules").forEachIndexed { i, rule ->
            val kind = if (rule.has("strings")) "text" else rule.path("match").asText()
            (rule.path("words").takeIf { it.isArray } ?: rule.path("strings")).forEach { w ->
                val prev = owners.put(kind to w.asText(), i)
                if (prev != null) problems += "规则 $prev 与规则 $i 在同一种匹配方式下重复了「${w.asText()}」"
            }
        }
        if (problems.isNotEmpty()) throw GradleException("推断命名规则校验失败：\n" + problems.joinToString("\n"))
        logger.lifecycle("verifyTypeInferenceRules: ${rules.path("rules").size()} 条规则、${vectors.size()} 条测试向量通过 schema")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyTypeInferenceRules) }
}
