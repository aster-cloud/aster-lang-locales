// 语言包测试共享夹具。公共配置（core 依赖、JDK toolchain）来自根构建的 `subprojects {}`；
// 各语言包以 testImplementation(project(":test-support")) 引用，本模块不发布。

dependencies {
    // 夹具与契约基类会被引用方的测试直接调用或继承，JUnit 与 AssertJ 以 api 暴露给引用方的测试编译期。
    "api"("org.junit.jupiter:junit-jupiter:6.0.0")
    "api"("org.assertj:assertj-core:3.27.3")
}

/**
 * verifyErrorCodesBaseline：本模块内的英文诊断码表副本
 * （src/main/resources/diagnostics/error_codes.json）必须与真源逐字节一致。
 *
 * 真源是 aster-lang-ts 的 shared/error_codes.json；aster-lang-core 的
 * src/test/resources/diagnostics/error_codes.json 是其逐字节副本，CI 总会检出 core，
 * 故优先找 ts、其次找 core。显式 -PerrorCodesBaseline=<path> 覆盖。
 * 找不到时：CI 下失败（缺真源不能看起来像通过），本地跳过并告警。
 *
 * 同步流程：码表变更后 cp <真源> test-support/src/main/resources/diagnostics/error_codes.json，
 * 并为各语言 overlay 补齐新码的译文（DiagnosticOverlayContract 会逐码校验）。
 */
tasks.register("verifyErrorCodesBaseline") {
    group = "verification"
    description = "Ensure the bundled error_codes.json copy matches aster-lang-ts/shared (or its core copy) byte-for-byte"

    val ours = file("src/main/resources/diagnostics/error_codes.json")
    val workspace = rootProject.projectDir.parentFile
    val override = providers.gradleProperty("errorCodesBaseline").orNull
    val candidates = listOf(
        workspace.resolve("aster-lang-ts/shared/error_codes.json"),
        workspace.resolve("aster-lang-core/src/test/resources/diagnostics/error_codes.json")
    )
    val baseline = override?.let { file(it) }
        ?: candidates.firstOrNull { it.exists() }
        ?: candidates.first()

    inputs.file(ours)
    inputs.files(baseline)

    doLast {
        if (!baseline.exists()) {
            if (System.getenv("CI") != null) {
                throw GradleException(
                    "verifyErrorCodesBaseline: baseline not found at ${baseline.absolutePath}; " +
                        "check out aster-lang-core (or aster-lang-ts) as a sibling or pass -PerrorCodesBaseline=<path>."
                )
            }
            logger.warn("verifyErrorCodesBaseline: baseline not found at ${baseline.absolutePath}; local run is skipping.")
            return@doLast
        }
        if (!ours.readBytes().contentEquals(baseline.readBytes())) {
            throw GradleException(
                "error_codes.json drift detected:\n" +
                    "  test-support copy: ${ours.absolutePath}\n" +
                    "  baseline:          ${baseline.absolutePath}\n" +
                    "Copy the baseline over the test-support copy and add translations for any new codes."
            )
        }
        logger.lifecycle("verifyErrorCodesBaseline: error_codes.json matches ${baseline.absolutePath} ✓")
    }
}

tasks.named("check") {
    dependsOn("verifyErrorCodesBaseline")
}
