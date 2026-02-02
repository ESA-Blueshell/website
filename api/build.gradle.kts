import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.15.0"
    java
}

group = "net.blueshell"
version = "1.0.0"

description = "The API for the Blueshell Esports website"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

configurations.configureEach {
    attributes.attribute(
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named(TargetJvmEnvironment.STANDARD_JVM)
    )
}

configurations.configureEach {
    exclude(group = "org.yaml", module = "snakeyaml")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation("com.nimbusds:nimbus-jose-jwt:10.5")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("com.google.apis:google-api-services-calendar:v3-rev20250404-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.39.1")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    compileOnly("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    implementation("org.flywaydb:flyway-core")

    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api")
    implementation("jakarta.transaction:jakarta.transaction-api")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.apache.tika:tika-core:3.2.3")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.15")
    implementation("com.github.scribejava:scribejava-apis:8.3.1")

    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    implementation("org.springframework.data:spring-data-jpa")
    implementation("jakarta.persistence:jakarta.persistence-api")

    implementation("org.flywaydb:flyway-mysql:11.13.2")

    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.openapitools:jackson-databind-nullable:0.2.7")

    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.5")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")

    implementation("com.github.javafaker:javafaker:1.0.2")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("io.rest-assured:spring-mock-mvc:5.5.6")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("com.google.apis:google-api-services-groupssettings:v1-rev20220614-2.0.0")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    implementation(files("libs/snakeyaml-2.5.jar"))
}

springBoot {
    mainClass.set("net.blueshell.api.ApiApplication")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(24)
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<BootRun>().configureEach {
    jvmArgs("-Dspring.devtools.restart.enabled=true")
}

val brevoOutputDir: Provider<Directory> = layout.buildDirectory.dir("generated/sources/openapi/brevo")

sourceSets["main"].java.srcDir(brevoOutputDir.map { it.dir("src/main/java") })

tasks.register<GenerateTask>("generateBrevoClient") {
    validateSpec.set(false)
    generatorName.set("java")
    library.set("restclient")
    inputSpec.set(layout.projectDirectory.file("../openapi/brevo.yml").asFile.absolutePath)
    outputDir.set(brevoOutputDir.get().asFile.absolutePath)
    apiPackage.set("net.blueshell.clients.brevo.api")
    modelPackage.set("net.blueshell.clients.brevo.model")
    invokerPackage.set("net.blueshell.clients.brevo.invoker")
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "jackson" to "true",
            "serializationLibrary" to "jackson",
        )
    )
    additionalProperties.set(
        mapOf(
            "withXml" to "false",
            "jackson" to "true",
            "serializationLibrary" to "jackson",
        )
    )
    inlineSchemaOptions.set(
        mapOf(
            "RESOLVE_INLINE_ENUMS" to "true",
        )
    )
    schemaMappings.set(
        mapOf(
            "getContactInfo_identifier_parameter" to "String",
            "updateContact_identifier_parameter" to "String",
            "createDoiContact_attributes_value" to "Object",
            "getContactInfo_identifierType_parameter" to "String",
            "updateContact_identifierType_parameter" to "String",
        )
    )
    globalProperties.set(
        mapOf(
            "apis" to "TransactionalEmails,Contacts",
            "models" to "",
            "supportingFiles" to "",
        )
    )
}

tasks.named("compileJava") {
    dependsOn("generateBrevoClient")
}
