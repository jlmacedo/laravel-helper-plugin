plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.laravel.plugin"
version = "1.0.138"

repositories {
    mavenCentral()
}

// We're not using the toolchain setup since it might not be available
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

intellij {
    type.set("IU")
    version.set("2024.3")

    plugins.set(
        listOf(
            "com.jetbrains.php:243.21565.193",
            "org.jetbrains.plugins.yaml",
            "com.intellij.modules.json"
        )
    )
}

/* ---------- Task configuration ---------- */
tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            languageVersion = "1.9"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    // Add a flag to ignore the Kotlin stdlib warning
    buildPlugin {
        System.setProperty("kotlin.stdlib.default.dependency", "false")
    }
    
    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("251")
    }

    buildSearchableOptions {  // optional – speeds up CI
        enabled = false
    }

    runIde {
        jvmArgs("-Xmx2048m")
    }
    
    test {
        useJUnit()
    }
}