val liquibaseVersion = rootProject.properties["liquibaseVersion"] as String
val kotestVersion = rootProject.properties["kotestVersion"] as String
val kotlinVersion = rootProject.properties["kotlinVersion"] as String
val liquibaseKotlinVersion = rootProject.properties["liquibaseKotlinVersion"] as String

repositories {
    mavenCentral()
}

dependencies {
    // liquibase-kotlin
    api(project(":serializer-core"))
    // liquibase
    implementation("org.liquibase:liquibase-core:$liquibaseVersion")
    // reflection
    api("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    // test
    testImplementation("io.kotest:kotest-framework-engine-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("kotest.framework.classpath.scanning.config.disable", "true")
}
