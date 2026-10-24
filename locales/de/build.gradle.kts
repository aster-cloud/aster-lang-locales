// German locale pack. Shared config from the root build's
// `subprojects {}`; this file declares the artifactId and the
// en-pack runtime dep its Canonicalizer tests need.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-locales-de"
        }
    }
}

dependencies {
    // Canonicalizer 内部依赖 en-US 词法表作为翻译目标（同 zh）。
    "testRuntimeOnly"(project(":en"))
}

/**
 * verifyLexiconKeywordParity (mirrors standalone aster-lang-de + locales/en):
 *   de-DE.json 的 `keywords` 键集必须与 en-US backbone（aster-lang-core 内嵌
 *   builtin/en-US.json）一致。各语言翻译值不同，但 SemanticTokenKind 键名必须
 *   跨所有 lexicon 完全相同，否则运行期翻译会静默漂移。
 *
 * 路径：合并后本模块位于 aster-lang-locales/locales/de/，core backbone 在
 * CI sibling 布局下相对路径为 ../../../aster-lang-core（同 locales/en）。
 * 路径优先级：显式 -PenLexiconBackbone 覆盖，其次嵌套检出，最后 ../ sibling。
 */
tasks.register("verifyLexiconKeywordParity") {
    group = "verification"
    description = "Ensure de-DE.json keyword set matches en-US backbone"

    val ours = file("src/main/resources/lexicons/de-DE.json")
    val override = providers.gradleProperty("enLexiconBackbone").orNull
    val backboneCandidates = listOf(
        file("../../../aster-lang-core/src/main/resources/builtin/en-US.json"),
        file("../aster-lang-core/src/main/resources/builtin/en-US.json")
    )
    val backbone = override?.let { file(it) }
        ?: backboneCandidates.firstOrNull { it.exists() }
        ?: backboneCandidates.first()

    inputs.file(ours)
    // FileCollection 容忍文件缺失；缺失时由 doLast 按 CI 与否决定失败或跳过。
    inputs.files(backbone)

    doLast {
        if (!backbone.exists()) {
            if (System.getenv("CI") != null) {
                throw GradleException(
                    "verifyLexiconKeywordParity: en-US backbone not found at ${backbone.absolutePath}; " +
                        "check out aster-lang-core or pass -PenLexiconBackbone=<path>."
                )
            }
            logger.warn("verifyLexiconKeywordParity: en-US backbone not found at ${backbone.absolutePath}; local run is skipping.")
            return@doLast
        }
        val parser = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val oursKeywords = ((parser.parse(ours) as Map<String, Any>)["keywords"] as Map<String, Any>).keys
        @Suppress("UNCHECKED_CAST")
        val backboneKeywords = ((parser.parse(backbone) as Map<String, Any>)["keywords"] as Map<String, Any>).keys

        val onlyInOurs = oursKeywords - backboneKeywords
        val onlyInBackbone = backboneKeywords - oursKeywords
        if (onlyInOurs.isNotEmpty() || onlyInBackbone.isNotEmpty()) {
            throw GradleException(
                "de-DE.json keyword drift:\n" +
                    "  only in de-DE: $onlyInOurs\n" +
                    "  only in en-US: $onlyInBackbone\n" +
                    "Sync the keyword set across all lexicon repos before merging."
            )
        }
        logger.lifecycle(
            "verifyLexiconKeywordParity: de-DE.json keyword set matches en-US backbone (${oursKeywords.size} keys) ✓"
        )
    }
}

tasks.named("check") {
    dependsOn("verifyLexiconKeywordParity")
}
