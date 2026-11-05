plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
    id("com.android.application") version "9.5.0" apply false
    id("com.google.devtools.ksp") version "2.3.13-2.0.5" apply false
}

allprojects {
    group = "com.atlas.ultra"
    version = "0.1.0-SNAPSHOT"
    
    repositories {
        google()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://libraries.minecraft.net/")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}