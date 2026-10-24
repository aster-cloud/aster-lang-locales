// English locale pack. Shared config comes from the root build's
// `subprojects {}` block; this file only declares the published
// artifactId and the en-specific verifyLexiconParity check.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-locales-en"
        }
    }
}

/**
 * verifyLexiconParity: 校验本模块的 en-US.json 与 aster-lang-core
 * 内嵌副本（resources/builtin/en-US.json）逐字节一致。
 *
 * 背景：core 自带 en-US.json 作为永久 backbone（FallbackLexicon 需要）。
 * 本 locale 包仍保留 lexicon 文件以向后兼容旧消费者，但**两份内容必须同步**。
 * CI 跑此 task：任一仓库改了 lexicon JSON 未同步另一份 → 立即 fail。
 *
 * 路径说明：合并后本模块位于 aster-lang-locales/locales/en/。CI（见
 * .github/workflows/ci.yml）把本仓库 checkout 到 $WORKSPACE/aster-lang-locales/，
 * aster-lang-core checkout 为 sibling $WORKSPACE/aster-lang-core/。故从本模块
 * projectDir（aster-lang-locales/locales/en）回到 workspace 根需上溯三层，
 * core backbone 的相对路径为 ../../../aster-lang-core（比旧的独立仓多两层）。
 *
 * 路径优先级：显式 -PenLexiconBackbone 覆盖，其次 CI 的嵌套检出，最后本地 ../ sibling。
 *
 * 同步流程：改完一方后，cp <src> <dst>，commit 两份。
 */
tasks.register("verifyLexiconParity") {
    group = "verification"
    description = "Ensure locales/en en-US.json matches aster-lang-core/builtin/en-US.json byte-for-byte"

    val ours = file("src/main/resources/lexicons/en-US.json")
    val override = providers.gradleProperty("enLexiconBackbone").orNull
    val coreCandidates = listOf(
        file("../../../aster-lang-core/src/main/resources/builtin/en-US.json"),
        file("../aster-lang-core/src/main/resources/builtin/en-US.json")
    )
    val coreCopy = override?.let { file(it) }
        ?: coreCandidates.firstOrNull { it.exists() }
        ?: coreCandidates.first()

    inputs.file(ours)
    // FileCollection 容忍文件缺失；缺失时由 doLast 按 CI 与否决定失败或跳过。
    inputs.files(coreCopy)

    doLast {
        if (!coreCopy.exists()) {
            if (System.getenv("CI") != null) {
                throw GradleException(
                    "verifyLexiconParity: en-US backbone not found at ${coreCopy.absolutePath}; " +
                        "check out aster-lang-core or pass -PenLexiconBackbone=<path>."
                )
            }
            logger.warn("verifyLexiconParity: en-US backbone not found at ${coreCopy.absolutePath}; local run is skipping.")
            return@doLast
        }
        val oursBytes = ours.readBytes()
        val coreBytes = coreCopy.readBytes()
        if (!oursBytes.contentEquals(coreBytes)) {
            throw GradleException(
                "en-US.json drift detected:\n" +
                    "  aster-lang-locales/locales/en: ${ours.absolutePath} (${oursBytes.size} bytes)\n" +
                    "  aster-lang-core:               ${coreCopy.absolutePath} (${coreBytes.size} bytes)\n" +
                    "Sync the two files (cp one to the other) and commit both before merging."
            )
        }
        logger.lifecycle("verifyLexiconParity: en-US.json matches between locales/en and aster-lang-core ✓")
    }
}

// Wire verifyLexiconParity into the standard check chain so CI runs it.
tasks.named("check") {
    dependsOn("verifyLexiconParity")
}
