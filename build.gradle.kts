plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("io.ktor.plugin") version "3.6.1"
}

group = "finance"
version = "0.1.0"

application {
    mainClass.set("finance.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("ch.qos.logback:logback-classic:1.6.6")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.ktor:ktor-client-mock")
}

kotlin {
    jvmToolchain(17)
}

ktor {
    fatJar {
        archiveFileName.set("finance.jar")
    }
}

tasks.test {
    useJUnitPlatform()
}
