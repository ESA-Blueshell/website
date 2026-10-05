plugins {
    id("kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        // The plugin's own BOM, so the managed versions move with the plugin a bot bumps.
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("tools.jackson:jackson-bom:3.2.3")
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "implementation"("io.micrometer:micrometer-registry-prometheus")
    "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
    "implementation"("org.jetbrains.kotlin:kotlin-reflect")
}
