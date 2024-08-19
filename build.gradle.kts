import java.net.URI

val kotestVersion: String by project
val kotlinVersion: String by project
val liquibaseVersion: String by project
val artifactId: String by project
val liquibaseKotlinDslVersion: String by project

plugins {
    kotlin("jvm") version "2.0.10"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "momosetkn"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven { url = URI("https://jitpack.io") }
    }
}

val libraryProjects = subprojects.filter {
    it.name in listOf("dsl", "parser", "serializer")
}
configure(libraryProjects) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    libraryProjects.forEach {
        from(project(":${it.name}").sourceSets["main"].allSource)
    }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set(artifactId)
    archiveVersion.set(liquibaseKotlinDslVersion)
    from(sourceSets["main"].output)
    from(sourcesJar)
    libraryProjects.forEach {
        from(project(":${it.name}").sourceSets["main"].output)
    }
}

publishing {
    publications {
        create<MavenPublication>("shadowJarPublication") {
            artifact(tasks["shadowJar"])
            artifact(sourcesJar)
            groupId = "com.github.momosetkn"
            artifactId = artifactId
            version = liquibaseKotlinDslVersion
        }
    }
    repositories {
        maven { url = URI("https://jitpack.io") }
    }
}

dependencies {
    // modules
    api(project(":dsl"))
    api(project(":parser"))
    api(project(":serializer"))

    // test
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-framework-engine-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")

    // db-migration
    implementation("org.liquibase:liquibase-core:$liquibaseVersion")
    implementation("info.picocli:picocli:4.7.6")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(listOf("-Xcontext-receivers", "-Xjvm-default=all"))
    }
}

ktlint {
    version.set("1.3.1")
}
