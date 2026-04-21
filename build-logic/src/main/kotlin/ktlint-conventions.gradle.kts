import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

configure<KtlintExtension> {
    android.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}
