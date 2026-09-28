buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 ships with Kotlin Gradle plugin 2.2.10, whose compiler cannot read
        // the Kotlin 2.4 metadata that current androidx artifacts are published
        // with ("expected version is 2.2.0"). Overriding the classpath here is
        // AGP's documented way to raise the built-in Kotlin version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
