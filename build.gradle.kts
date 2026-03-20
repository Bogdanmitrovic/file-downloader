plugins {
    kotlin("jvm") version "2.3.0"
}

group = "org.bogdanmitrovic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}