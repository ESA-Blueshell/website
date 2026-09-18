import dev.detekt.gradle.Detekt
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

// The aggregate `detekt` task that the plugin puts on `check` covers main and
// test only. Integration tests and test fixtures get tasks of their own, so
// without this they are analysed by nobody. The `*SourceSet` tasks are the
// source-only ones; the type-resolution variants stay off `check` because they
// need the whole thing compiled first.
tasks.named("check") {
    dependsOn(tasks.withType<Detekt>().matching { it.name.endsWith("SourceSet") })
}
