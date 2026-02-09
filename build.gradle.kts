plugins {
    base
}

allprojects {
    group = "net.blueshell"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/konvert/maven")
    }
}
