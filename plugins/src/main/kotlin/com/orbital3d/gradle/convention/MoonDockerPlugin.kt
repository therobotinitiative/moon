package com.orbital3d.gradle.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.Action
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import javax.inject.Inject
// no explicit TaskProvider import; use the type returned by `tasks.register`
import java.io.File

abstract class MoonBuildTask @javax.inject.Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Input
    abstract val imageName: Property<String>

    @get:Input
    abstract val imageVersion: Property<String>

    @get:Input
    abstract val dockerfile: Property<String>

    @get:Input
    abstract val contextDir: Property<String>

    @get:Input
    @get:Optional
    abstract val uid: Property<String>

    @get:Input
    @get:Optional
    abstract val gid: Property<String>


    @TaskAction
    fun buildDockerImage() {
        val name = imageName.get()
        val ver = imageVersion.get()
        val df = dockerfile.get()
        val ctx = contextDir.get()

        val image = "$name:$ver"
        logger.lifecycle("Building docker image: $image (dockerfile=$df, context=$ctx)")

        val dockerFile = File(df)
        val contextFile = File(ctx)
        if (!contextFile.exists()) {
            throw RuntimeException("Docker build context does not exist: $ctx")
        }

        val cmd = mutableListOf("docker", "build", "-t", image)
        if (dockerFile.exists()) {
            cmd += listOf("-f", dockerFile.absolutePath)
        }
        val uidVal = uid.orNull?.takeIf { it.isNotBlank() }
        val gidVal = gid.orNull?.takeIf { it.isNotBlank() }

        fun detectThroughExec(args: List<String>): String {
            val out = java.io.ByteArrayOutputStream()
            val res = execOperations.exec { execSpec ->
                execSpec.commandLine(args)
                execSpec.standardOutput = out
            }
            res.assertNormalExitValue()
            return out.toString().trim()
        }

        val finalUid = uidVal ?: try { detectThroughExec(listOf("id", "-u")) } catch (e: Exception) { "Could not get user id" }
        val finalGid = gidVal ?: try { detectThroughExec(listOf("id", "-g")) } catch (e: Exception) { "Could not get group id" }

        if (finalUid.isNotBlank() && finalGid.isNotBlank()) {
            cmd += listOf("--build-arg", "UID=$finalUid", "--build-arg", "GID=$finalGid")
        }
        cmd += listOf(contextFile.absolutePath)
        val result = execOperations.exec { execSpec ->
            execSpec.commandLine(*cmd.toTypedArray())
            execSpec.isIgnoreExitValue = false
        }

        if (result.exitValue != 0) {
            throw RuntimeException("docker build failed with exit code ${result.exitValue}")
        }
    }
}

class MoonDockerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.findByName("moon") as? com.orbital3d.gradle.extension.MoonExtension

        val provider: TaskProvider<MoonBuildTask> = project.tasks.register("moonbuild", MoonBuildTask::class.java)
        provider.configure { t ->
            if (ext != null) {
                t.imageName.set(ext.imageName)
                t.imageVersion.set(ext.imageVersion)
                t.uid.set(ext.uid)
                t.gid.set(ext.gid)
            } else {
                t.imageName.set("${project.name}-moon")
                t.imageVersion.set("1.0.0")
            }
            t.contextDir.set(project.projectDir.absolutePath)
            t.dockerfile.set(project.file("Dockerfile").absolutePath)
        }
    }
}
