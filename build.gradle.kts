// Root build for the consolidated first-party locale packs (ADR 0011).
//
// Each module under locales/<id> is a thin java-library that ships one
// SPI-registered lexicon pack. The build config is shared here via
// `subprojects {}` so the three modules don't each carry a near-identical
// build.gradle.kts (the duplication that motivated the consolidation).
//
// Coordinates are preserved: every module publishes
// `cloud.aster-lang:aster-lang-<id>` exactly as the standalone repos did,
// so existing Maven consumers (aster-api, aster-lang-core tests, …) keep
// resolving the same artifact ids during the deprecation window.

// 共享版本目录句柄（aster-lang-platform，ADR 0012）。subprojects {} 块里
// 无法用 root 生成的 asterLibs.* type-safe 访问器，故通过
// VersionCatalogsExtension 显式查表，效果等价。
val asterLibs: VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("asterLibs")
val asterCore = asterLibs.findLibrary("core").get()

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "cloud.aster-lang"
    version = "1.0.2"

    repositories {
        mavenLocal()
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    dependencies {
        // The Aster compiler core — provides the lexicon/SPI interfaces
        // each pack implements. Version from the shared catalog.
        add("implementation", asterCore)
        add("testImplementation", "org.junit.jupiter:junit-jupiter:6.0.0")
        add("testImplementation", "org.assertj:assertj-core:3.27.3")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/aster-cloud/aster-lang-locales")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}
