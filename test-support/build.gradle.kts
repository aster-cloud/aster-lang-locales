// 语言包测试共享夹具。公共配置（core 依赖、JDK toolchain）来自根构建的 `subprojects {}`；
// 各语言包以 testImplementation(project(":test-support")) 引用，本模块不发布。

dependencies {
    // 夹具会被引用方的测试直接调用，JUnit 与 AssertJ 以 api 暴露给引用方的测试编译期。
    "api"("org.junit.jupiter:junit-jupiter:6.0.0")
    "api"("org.assertj:assertj-core:3.27.3")
}
