plugins {
    java
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.lingce"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("252.*")
    }

    buildSearchableOptions {
        enabled = false
    }
}
