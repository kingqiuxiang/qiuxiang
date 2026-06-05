plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.lingce.cleaner"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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

    withType<JavaCompile> {
        options.release.set(17)
    }
}
