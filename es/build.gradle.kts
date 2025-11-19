/*
 * Build file for the Elasticsearch plugin module (renamed from `lib`).
 */

group = "io.github.maiorsi.elasticsearch"
version = "0.1.0"

plugins {
    // Apply the java-library plugin for API and implementation separation.
    id("base")
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.roaringbitmap:RoaringBitmap:1.3.0")
    compileOnly("org.elasticsearch:elasticsearch:9.2.0")
    testImplementation("org.elasticsearch:elasticsearch:9.2.0")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.register(name = "distZip", type = Zip::class) {
     dependsOn(tasks.named("build"))
     from(layout.buildDirectory.dir("libs")) {
         include("*.jar")
     }
     from(layout.projectDirectory.dir("src/assembly")) {
         include("plugin-descriptor.properties")
     }
     from(configurations.runtimeClasspath)

     archiveFileName.set("es-rb-plugin.zip")
     destinationDirectory.set(file(layout.buildDirectory.dir("distributions")))
 }
