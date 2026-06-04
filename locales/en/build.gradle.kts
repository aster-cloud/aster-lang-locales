// English locale pack. Shared config comes from the root build's
// `subprojects {}` block; this file only declares the published
// artifactId and the en-specific verifyLexiconParity check.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-en"
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
 * 路径说明：合并后本模块位于 aster-lang-locales/locales/en/，故 core
 * 副本相对路径为 ../../../aster-lang-core（比旧的独立仓多两层）。
 *
 * 同步流程：改完一方后，cp <src> <dst>，commit 两份。
 */
tasks.register("verifyLexiconParity") {
    group = "verification"
    description = "Ensure locales/en en-US.json matches aster-lang-core/builtin/en-US.json byte-for-byte"

    val ours = file("src/main/resources/lexicons/en-US.json")
    val coreCopy = file("../../../aster-lang-core/src/main/resources/builtin/en-US.json")

    inputs.file(ours)

    doLast {
        if (!coreCopy.exists()) {
            logger.warn(
                "verifyLexiconParity: aster-lang-core builtin/en-US.json not found at ${coreCopy.absolutePath}. " +
                    "Skipping (sibling repo absent — likely non-monorepo CI). " +
                    "Run from a workspace where both repos are checked out."
            )
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
