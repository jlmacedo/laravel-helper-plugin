rootProject.name = "laravel-plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
    }
}