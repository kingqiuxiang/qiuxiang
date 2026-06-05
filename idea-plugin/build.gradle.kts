plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.lingce.cleaner"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set("2024.2")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("253.*")
    }

    buildSearchableOptions {
        enabled = false
    }
}
