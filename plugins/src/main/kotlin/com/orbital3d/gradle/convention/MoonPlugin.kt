package com.orbital3d.gradle.convention

import com.orbital3d.gradle.task.CheckProperties
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.orbital3d.gradle.extension.MoonExtension
import java.util.Properties
import java.io.FileInputStream

class MoonPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create(
            "moon",
            MoonExtension::class.java
        )

        // Load a subproject-local gradle.properties if present and apply to the extension as conventions.
        val localProps = Properties()
        val localFile = project.file("gradle.properties")
        if (localFile.exists()) {
            FileInputStream(localFile).use { localProps.load(it) }
        }

        fun firstProp(vararg keys: String): String? {
            for (k in keys) {
                val v = project.findProperty(k) ?: localProps.getProperty(k)
                if (v != null) return v.toString()
            }
            return null
        }

        // Note: UID/GID detection must not start external processes during configuration.
        // Leave uid/gid unset here and resolve them at task execution time instead.

        // Ensure a root-level `moonDefaults` extension exists so the root project can supply a
        // pluggable `PortProvider` instance. Subprojects may still override via their
        // local `gradle.properties` (e.g. `${project.name}.port`) — that remains optional.
        val rootDefaults = project.rootProject.extensions.findByName("moonDefaults") as? com.orbital3d.gradle.extension.MoonDefaultsExtension
            ?: project.rootProject.extensions.create(
                "moonDefaults",
                com.orbital3d.gradle.extension.MoonDefaultsExtension::class.java
            )

        val provider = rootDefaults.portProvider ?: com.orbital3d.gradle.api.DefaultPortProvider()
        val defaultPort = provider.defaultPort(project.name)
        if (defaultPort == -1) {
            throw RuntimeException("Missing (project name).port property or no suitable com.orbital3d.gradle.api.DefaultPortProvider supplied")
        }

        ext.port.convention(firstProp("${project.name}.port")?.toInt() ?: defaultPort)
        ext.user.convention(firstProp("${project.name}.user") ?: "${project.name}")
        ext.database.convention(firstProp("${project.name}.database") ?: project.name)
        ext.storagePath.convention(firstProp("${project.name}.storage") ?: "/var/lib/${project.name}")
        ext.imageName.convention(firstProp("${project.name}.image.name") ?: "${project.name}-moon")
        ext.imageVersion.convention(firstProp("${project.name}.image.version") ?: "1.0.0")
        val uidProp = firstProp("${project.name}.uid", "uid")
        if (uidProp != null) ext.uid.convention(uidProp)
        val gidProp = firstProp("${project.name}.gid", "gid")
        if (gidProp != null) ext.gid.convention(gidProp)

        val checkPropertiesProvider = project.tasks.register("checkProperties", CheckProperties::class.java)
        checkPropertiesProvider.configure { t ->
            t.port.set(ext.port.map { v: Int -> v.toString() })
            t.user.set(ext.user)
            t.database.set(ext.database)
            t.storagePath.set(ext.storagePath)
            t.imageName.set(ext.imageName)
            t.imageVersion.set(ext.imageVersion)
        }
    }
}
