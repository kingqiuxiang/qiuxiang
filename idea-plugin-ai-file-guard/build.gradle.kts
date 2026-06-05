plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.aifileguard"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.3")
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "243.*"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }
}
