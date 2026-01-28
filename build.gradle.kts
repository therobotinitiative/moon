plugins {
    base
}

tasks.register("publishPluginToMavenLocal") {
    dependsOn(":plugins:publishMavenPublicationToMavenLocal")
    group = "publishing"
    description = "Publishes the :plugins module to mavenLocal"
}