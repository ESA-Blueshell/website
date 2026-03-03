plugins {
    base
    val kotlinVersion = "2.3.10"
    kotlin("jvm") version kotlinVersion apply false
    kotlin("plugin.spring") version kotlinVersion apply false
    kotlin("plugin.jpa") version kotlinVersion apply false
    kotlin("plugin.allopen") version kotlinVersion apply false
    kotlin("plugin.noarg") version kotlinVersion apply false
    kotlin("kapt") version kotlinVersion apply false
}

allprojects {
    group = "net.blueshell"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}
