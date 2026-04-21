plugins {
    id("kotlin-conventions")
}

group = "net.blueshell"
version = "0.0.1-SNAPSHOT"

// Shared Kotlin helpers for services (Vault/OIDC/JWT/forward-auth).
// Populated in the OIDC introduction step; empty skeleton for now so
// services can start depending on :libs:kotlin-common without waiting.
dependencies {
    // Keep this list small — downstream services should be free to pick
    // their own web/security stacks. Only add things every consumer needs.
    compileOnly("org.springframework.security:spring-security-oauth2-jose")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
}
