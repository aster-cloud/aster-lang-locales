// Simplified-Chinese locale pack. Shared config from the root build's
// `subprojects {}`; this file declares the artifactId and the
// en-pack runtime dep its Canonicalizer tests need.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-locales-zh"
        }
    }
}

dependencies {
    // Canonicalizer 内部依赖 en-US 词法表作为翻译目标。合并前这是
    // testRuntimeOnly("cloud.aster-lang:aster-lang-en")；现在直接用
    // 兄弟模块，避免依赖 Maven Local 中可能过期的 en 工件。
    "testRuntimeOnly"(project(":en"))
}

/**
 * verifyLexiconKeywordParity (mirrors standalone aster-lang-zh + locales/en):
 *   zh-CN.json 的 `keywords` 键集必须与 en-US backbone（aster-lang-core 内嵌
 *   builtin/en-US.json）一致。各语言的翻译值不同，但 SemanticTokenKind 键名
 *   必须跨所有 lexicon 完全相同，否则运行期翻译会静默漂移。
 *
 * 路径：合并后本模块位于 aster-lang-locales/locales/zh/，core backbone 在
 * CI sibling 布局下相对路径为 ../../../aster-lang-core（同 locales/en）。
 * 按候选列表探测以兼容不同 checkout 嵌套深度。
 */
tasks.register("verifyLexiconKeywordParity") {
    group = "verification"
    description = "Ensure zh-CN.json keyword set matches en-US backbone"

    val ours = file("src/main/resources/lexicons/zh-CN.json")
    val backboneCandidates = listOf(
        "../../../aster-lang-core/src/main/resources/builtin/en-US.json",
        "../../../../aster-lang-core/src/main/resources/builtin/en-US.json",
        "../aster-lang-core/src/main/resources/builtin/en-US.json"
    ).map { file(it) }
    val backbone = backboneCandidates.firstOrNull { it.exists() } ?: backboneCandidates.first()

    inputs.file(ours)
    inputs.files(backboneCandidates).optional()

    doLast {
        if (!backbone.exists()) {
            logger.warn(
                "verifyLexiconKeywordParity: en-US backbone not found (tried ${backboneCandidates.map { it.path }}). " +
                    "Sibling aster-lang-core absent — likely non-monorepo CI. Skipping."
            )
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
                "zh-CN.json keyword drift:\n" +
                    "  only in zh-CN: $onlyInOurs\n" +
                    "  only in en-US: $onlyInBackbone\n" +
                    "Sync the keyword set across all lexicon repos before merging."
            )
        }
        logger.lifecycle(
            "verifyLexiconKeywordParity: zh-CN.json keyword set matches en-US backbone (${oursKeywords.size} keys) ✓"
        )
    }
}

tasks.named("check") {
    dependsOn("verifyLexiconKeywordParity")
}
