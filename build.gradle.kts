import org.gradle.api.tasks.Exec

plugins {
    // no-op, this file only defines tasks that orchestrate other projects
}

tasks.register<Exec>("dockerComposeUp") {
    group = "application"
    description = "Builds the plugin (:lib:distZip) then runs docker compose up --build"

    // Ensure the plugin distribution is built first
    dependsOn(":lib:distZip")

    // Command to run - runs docker compose from the repository root
    commandLine = listOf("docker", "compose", "up", "--build", "-d")

    // Forward stdout/stderr
    isIgnoreExitValue = false
}

tasks.register<Exec>("dockerComposeDown") {
    group = "application"
    description = "Runs docker compose down to stop the Elasticsearch instance"

    // Command to run - runs docker compose from the repository root
    commandLine = listOf("docker", "compose", "down")

    // Forward stdout/stderr
    isIgnoreExitValue = false
}
