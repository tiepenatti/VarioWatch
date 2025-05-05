plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
    setFollowSymlinks(false)
    mustRunAfter("cleanBuildDir")
}

tasks.register("cleanBuildDir", Delete::class) {
    delete(fileTree("build") {
        exclude("intermediates/**/*.lock")
    })
    setFollowSymlinks(false)
}