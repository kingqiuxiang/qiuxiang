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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    version.set("2024.1")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("253.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    withType<JavaCompile> {
        options.release.set(17)
    }
}
