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
}

// First-party locale packs consolidated from the formerly-separate
// aster-lang-en / aster-lang-zh / aster-lang-de repos (ADR 0011).
// Each remains an independently-publishable Maven artifact with the
// SAME coordinates it had before the consolidation
// (cloud.aster-lang:aster-lang-{en,zh,de}); the only change is that
// they now share one build + version graph.
include(":en", ":zh", ":de")
project(":en").projectDir = file("locales/en")
project(":zh").projectDir = file("locales/zh")
project(":de").projectDir = file("locales/de")
