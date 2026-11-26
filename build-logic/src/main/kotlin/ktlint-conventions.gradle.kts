import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

configure<KtlintExtension> {
    android.set(false)
    // Files formatting would rewrite on lines no unit test runs, so the changed-lines gate
    // (testing ADR-007) would refuse the reformat. A file leaves this list when a change that
    // covers it formats it; nothing is ever added to it. ktlintFormat ignores this list and
    // rewrites those files too, so revert the ones a change does not cover.
    baseline.set(layout.projectDirectory.file("ktlint-baseline.xml"))
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// Spring dependency management pins ktlint's embedded Kotlin compiler to the project's Kotlin
// version, and ktlint 1.5.0 cannot parse with 2.4.21. Move this with the ktlint version.
afterEvaluate {
    configurations.named("ktlint") {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compiler-embeddable") {
                useVersion("2.4.10")
            }
        }
    }
}
