plugins {
    id("kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
        mavenBom("tools.jackson:jackson-bom:3.1.0")
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "implementation"("io.micrometer:micrometer-registry-prometheus")
    "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
    "implementation"("org.jetbrains.kotlin:kotlin-reflect")
}
