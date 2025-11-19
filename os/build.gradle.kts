/*
 * Build file for the OpenSearch variant of the plugin.
 */

group = "io.github.maiorsi.opensearch"
version = "0.1.0"

plugins {
    id("base")
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.roaringbitmap:RoaringBitmap:1.3.0")
    compileOnly("org.opensearch:opensearch:3.3.2")
    testImplementation("org.opensearch:opensearch:3.3.2")
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
     archiveFileName.set("os-rb-plugin.zip")
     destinationDirectory.set(file(layout.buildDirectory.dir("distributions")))
 }
