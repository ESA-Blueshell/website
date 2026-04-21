import dev.detekt.gradle.extensions.DetektExtension

plugins {
    id("dev.detekt")
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    val configFile = file("${project.rootDir}/config/detekt/detekt.yml")
    if (configFile.exists()) {
        config.setFrom(files(configFile))
    }
}
