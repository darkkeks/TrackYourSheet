import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.61"
    id("application")
    id("com.github.johnrengelman.shadow") version "4.0.1"
}

group = "ru.darkkeks"
version = "1.0-SNAPSHOT"

application {
    mainClassName = "ru.darkkeks.trackyoursheet.ZhopKt"
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")

    implementation("org.apache.logging.log4j", "log4j-slf4j-impl", "2.8.2")
    implementation("org.apache.logging.log4j", "log4j-api", "2.8.2")
    implementation("org.apache.logging.log4j", "log4j-core", "2.8.2")

    implementation("org.litote.kmongo:kmongo-coroutine:3.11.2")

    implementation("org.kodein.di:kodein-di-generic-jvm:6.5.0")

    implementation("com.github.pengrad:java-telegram-bot-api:4.4.0")

    implementation("com.google.api-client:google-api-client:1.30.4")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.30.4")
    implementation("com.google.apis:google-api-services-sheets:v4-rev581-1.25.0")

    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
}
