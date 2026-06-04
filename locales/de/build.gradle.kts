// German locale pack. Shared config from the root build's
// `subprojects {}`; this file declares the artifactId and the
// en-pack runtime dep its Canonicalizer tests need.

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-de"
        }
    }
}

dependencies {
    // Canonicalizer 内部依赖 en-US 词法表作为翻译目标（同 zh）。
    "testRuntimeOnly"(project(":en"))
}
