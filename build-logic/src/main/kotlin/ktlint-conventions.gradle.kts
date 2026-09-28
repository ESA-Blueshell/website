import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

configure<KtlintExtension> {
    android.set(false)
    // Files formatting would rewrite on lines no unit test runs, so the changed-lines gate
    // (testing ADR-007) would refuse the reformat. A file leaves this list when a change that
    // covers it formats it; nothing is ever added to it.
    baseline.set(layout.projectDirectory.file("ktlint-baseline.xml"))
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}
