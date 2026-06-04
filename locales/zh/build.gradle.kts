// Simplified-Chinese locale pack. Shared config from the root build's
// `subprojects {}`; this file declares the artifactId and the
// en-pack runtime dep its Canonicalizer tests need.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-zh"
        }
    }
}

dependencies {
    // Canonicalizer 内部依赖 en-US 词法表作为翻译目标。合并前这是
    // testRuntimeOnly("cloud.aster-lang:aster-lang-en")；现在直接用
    // 兄弟模块，避免依赖 Maven Local 中可能过期的 en 工件。
    "testRuntimeOnly"(project(":en"))
}
