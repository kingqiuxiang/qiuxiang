plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.lingce"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "253.*"
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
