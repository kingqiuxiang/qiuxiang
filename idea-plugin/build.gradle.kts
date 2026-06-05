plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.lingce"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.5")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        name = "LingCe AI File Cleaner"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
            untilBuild = "253.*"
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
