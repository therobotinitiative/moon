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
        val localProperties = Properties()
        val localPropertyFile = project.file("gradle.properties")
        if (localPropertyFile.exists()) {
            FileInputStream(localPropertyFile).use { localProperties.load(it) }
        }

        fun firstProp(vararg keys: String): String? {
            for (key in keys) {
                val value = project.findProperty(key) ?: localProperties.getProperty(key)
                if (value != null) return value.toString()
            }
            return null
        }

        // Note: UID/GID detection must not start external processes during configuration.
        // Leave uid/gid unset here and resolve them at task execution time instead.

        // Resolve default port from the built-in DefaultPortProvider.
        // Projects can override via gradle.properties (e.g. `projectname.port=1234`).
        val portProvider = com.orbital3d.gradle.api.DefaultPortProvider()
        val defaultPort = portProvider.defaultPort(project.name)

        val portProp = firstProp("${project.name}.port")?.toInt()
        if (portProp != null) {
            ext.port.convention(portProp)
        } else if (defaultPort != -1) {
            ext.port.convention(defaultPort)
        }
        ext.user.convention(firstProp("${project.name}.user") ?: "${project.name}")
        ext.database.convention(firstProp("${project.name}.database") ?: project.name)
        ext.storagePath.convention(firstProp("${project.name}.storage") ?: "/var/lib/forest/${project.name}")
        ext.imageName.convention(firstProp("${project.name}.image.name") ?: "${project.name}-moon")
        ext.imageVersion.convention(firstProp("${project.name}.image.version") ?: "1.0.0")
        val uidProp = firstProp("${project.name}.uid", "uid")
        if (uidProp != null) ext.uid.convention(uidProp)
        val gidProp = firstProp("${project.name}.gid", "gid")
        if (gidProp != null) ext.gid.convention(gidProp)

        val checkPropertiesProvider = project.tasks.register("checkProperties", CheckProperties::class.java)
        checkPropertiesProvider.configure { t ->
            t.port.set(ext.port.map { valu: Int -> valu.toString() })
            t.user.set(ext.user)
            t.database.set(ext.database)
            t.storagePath.set(ext.storagePath)
            t.imageName.set(ext.imageName)
            t.imageVersion.set(ext.imageVersion)
        }
    }
}
