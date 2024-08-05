val kotestVersion: String by project

plugins {
    kotlin("jvm") version "2.0.0"
}

group = "innoscouter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // test
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-framework-engine-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")

    // db-migration
    implementation("org.liquibase:liquibase-core:4.27.0")
    implementation("info.picocli:picocli:4.7.6")
    implementation(kotlin("stdlib-jdk8"))
}
kotlin {
    jvmToolchain(21)
}
