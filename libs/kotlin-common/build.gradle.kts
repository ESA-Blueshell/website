plugins {
    id("kotlin-conventions")
    id("io.spring.dependency-management")
}

group = "net.blueshell"
version = "0.0.1-SNAPSHOT"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
    }
}

// Shared Kotlin helpers for services (Vault/OIDC/JWT/forward-auth).
dependencies {
    compileOnly("org.springframework.security:spring-security-oauth2-jose")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
}
