plugins {
    id("kotlin-conventions")
}

group = "net.blueshell"
version = "0.0.1-SNAPSHOT"

// Shared Kotlin helpers for services (Vault/OIDC/JWT/forward-auth).
dependencies {
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    compileOnly("org.springframework.security:spring-security-oauth2-jose")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.vault:spring-vault-core:4.0.1")
}
