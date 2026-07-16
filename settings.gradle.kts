rootProject.name = "aster-lang-locales"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    // 共享版本目录（aster-lang-platform，ADR 0012）：aster-lang 生态依赖
    // 版本的单一来源。用 asterLibs.* 别名代替散落的版本字面量。
    versionCatalogs {
        create("asterLibs") {
            from("cloud.aster-lang:aster-lang-platform:1.0.16")
        }
    }
}

// First-party locale packs consolidated from the formerly-separate
// aster-lang-en / aster-lang-zh / aster-lang-de repos (ADR 0011).
// Each remains an independently-publishable Maven artifact, but publishes
// under a NEW artifactId — cloud.aster-lang:aster-lang-locales-{en,zh,de}
// (see locales/*/build.gradle.kts:9), NOT the pre-consolidation
// cloud.aster-lang:aster-lang-{en,zh,de}. Reason: GitHub Packages 422s on
// package names still owned by the now-archived standalone repos, so the
// coordinates could not be reused. Version source of truth is the
// aster-lang-platform version catalog (asterLibs.*, from(...) above).
include(":en", ":zh", ":de")
project(":en").projectDir = file("locales/en")
project(":zh").projectDir = file("locales/zh")
project(":de").projectDir = file("locales/de")
