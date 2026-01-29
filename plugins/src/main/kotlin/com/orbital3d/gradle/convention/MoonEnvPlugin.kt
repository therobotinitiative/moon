package com.orbital3d.gradle.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.model.ObjectFactory
import org.gradle.process.ExecOperations
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File

open class MoonEnvExtension @Inject constructor(objects: ObjectFactory) {
    val networkName: Property<String> = objects.property(String::class.java)
    val userName: Property<String> = objects.property(String::class.java)
    val groupName: Property<String> = objects.property(String::class.java)
    val uid: Property<String> = objects.property(String::class.java)
    val gid: Property<String> = objects.property(String::class.java)
    val integrateWithBuildEnvironment: Property<Boolean> = objects.property(Boolean::class.java)
    val skipIfNoPrivs: Property<Boolean> = objects.property(Boolean::class.java)
    val dryRun: Property<Boolean> = objects.property(Boolean::class.java)

    init {
        networkName.set("forest.network")
        userName.set("forest.user")
        groupName.set("forest.group")
        uid.convention("")
        gid.convention("")
        integrateWithBuildEnvironment.convention(false)
        skipIfNoPrivs.convention(false)
        dryRun.convention(false)
    }
}

abstract class CreateDockerNetworkTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val networkName: Property<String>

    @TaskAction
    fun createNetwork() {
        val name = networkName.get()
        logger.lifecycle("Checking docker network: $name")

        val out = ByteArrayOutputStream()
        val inspect = execOperations.exec { spec ->
            spec.commandLine("docker", "network", "inspect", name)
            spec.isIgnoreExitValue = true
            spec.standardOutput = out
            spec.errorOutput = out
        }
        if (inspect.exitValue == 0) {
            logger.lifecycle("Docker network '$name' already exists")
            return
        }

        logger.lifecycle("Creating docker network: $name")
        val createOut = ByteArrayOutputStream()
        val created = execOperations.exec { spec ->
            spec.commandLine("docker", "network", "create", name)
            spec.isIgnoreExitValue = false
            spec.standardOutput = createOut
            spec.errorOutput = createOut
        }
        if (created.exitValue != 0) {
            throw RuntimeException("Failed to create docker network '$name': ${createOut.toString()}")
        }
        logger.lifecycle("Docker network '$name' created")
    }
}

abstract class EnsureHostUserGroupTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val userName: Property<String>

    @get:Input
    abstract val groupName: Property<String>

    @get:Input
    @get:Optional
    abstract val uid: Property<String>

    @get:Input
    @get:Optional
    abstract val gid: Property<String>

    @get:Input
    abstract val skipIfNoPrivs: Property<Boolean>

    @get:Input
    abstract val dryRun: Property<Boolean>

    private fun runCommand(args: List<String>, ignoreExit: Boolean = false): Int {
        val out = ByteArrayOutputStream()
        val res = execOperations.exec { spec ->
            spec.commandLine(args)
            spec.isIgnoreExitValue = ignoreExit
            spec.standardOutput = out
            spec.errorOutput = out
        }
        return res.exitValue
    }

    private fun currentUid(): Int {
        return try {
            val out = ByteArrayOutputStream()
            val res = execOperations.exec { spec ->
                spec.commandLine("id", "-u")
                spec.isIgnoreExitValue = false
                spec.standardOutput = out
            }
            out.toString().trim().toInt()
        } catch (e: Exception) {
            -1
        }
    }

    @TaskAction
    fun ensure() {
        val user = userName.get()
        val group = groupName.get()
        val dry = dryRun.getOrElse(false)
        val skipNoPriv = skipIfNoPrivs.getOrElse(false)

        val uidInt = try { uid.orNull?.takeIf { it.isNotBlank() }?.toInt() } catch (e: Exception) { null }
        val gidInt = try { gid.orNull?.takeIf { it.isNotBlank() }?.toInt() } catch (e: Exception) { null }

        val curUid = currentUid()
        if (curUid != 0 && skipNoPriv) {
            logger.lifecycle("Not running as root; skipping host user/group creation as requested")
            return
        }
        if (dry) {
            logger.lifecycle("[dry-run] Would ensure group '$group' and user '$user' (uid=$uidInt gid=$gidInt)")
            return
        }

        // ensure group
        logger.lifecycle("Checking group: $group")
        val groupExists = runCommand(listOf("getent", "group", group), true) == 0
        if (!groupExists) {
            val gcmd = mutableListOf("groupadd")
            if (gidInt != null) { gcmd += listOf("-g", gidInt.toString()) }
            gcmd += group
            logger.lifecycle("Creating group: ${gcmd.joinToString(" ")}")
            val gres = runCommand(gcmd)
            if (gres != 0) throw RuntimeException("groupadd failed with exit code $gres")
        } else {
            logger.lifecycle("Group '$group' already exists")
        }

        // ensure user
        logger.lifecycle("Checking user: $user")
        val userExists = runCommand(listOf("id", "-u", user), true) == 0
        if (!userExists) {
            val ucmd = mutableListOf("useradd", "-M", "-s", "/usr/sbin/nologin", "-g", group)
            if (uidInt != null) { ucmd += listOf("-u", uidInt.toString()) }
            ucmd += user
            logger.lifecycle("Creating user: ${ucmd.joinToString(" ")}")
            val ures = runCommand(ucmd)
            if (ures != 0) throw RuntimeException("useradd failed with exit code $ures")
        } else {
            logger.lifecycle("User '$user' already exists")
        }
    }
}

abstract class PrepareStoragePathTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val storagePath: Property<String>

    @get:Input
    @get:Optional
    abstract val owner: Property<String>

    @get:Input
    @get:Optional
    abstract val group: Property<String>

    @get:Input
    @get:Optional
    abstract val mode: Property<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @TaskAction
    fun prepare() {
        val path = storagePath.get()
        val f = File(path)
        if (!f.exists()) {
            logger.lifecycle("Creating storage path: $path")
            val created = f.mkdirs()
            if (!created && !f.exists()) throw RuntimeException("Failed to create path: $path")
        } else {
            logger.lifecycle("Storage path exists: $path")
        }

        val dry = dryRun.getOrElse(false)
        val ownerVal = owner.orNull
        val groupVal = group.orNull
        val modeVal = mode.orNull

        if (!dry) {
            if (ownerVal != null || groupVal != null) {
                val target = if (ownerVal != null && groupVal != null) "${ownerVal}:${groupVal}" else (ownerVal ?: ":${groupVal}")
                logger.lifecycle("Setting ownership $target on $path")
                val chownCmd = listOf("chown", "-R", target, path)
                val res = execOperations.exec { spec ->
                    spec.commandLine(chownCmd)
                    spec.isIgnoreExitValue = false
                }
                if (res.exitValue != 0) throw RuntimeException("chown failed with exit code ${res.exitValue}")
            }
            if (modeVal != null) {
                logger.lifecycle("Setting mode $modeVal on $path")
                val chmodCmd = listOf("chmod", "-R", modeVal, path)
                val cres = execOperations.exec { spec ->
                    spec.commandLine(chmodCmd)
                    spec.isIgnoreExitValue = false
                }
                if (cres.exitValue != 0) throw RuntimeException("chmod failed with exit code ${cres.exitValue}")
            }
        } else {
            logger.lifecycle("[dry-run] Would set owner=${ownerVal} group=${groupVal} mode=${modeVal} on $path")
        }
    }
}

class MoonEnvPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext: MoonEnvExtension = project.extensions.create("moonEnv", MoonEnvExtension::class.java, project.objects)

        // Register subproject task (also applies to root so subprojects can use it)
            project.tasks.register("prepareStoragePath", PrepareStoragePathTask::class.java) { t ->
            t.setGroup("moon")
            t.setDescription("Create storage path and set ownership/permissions as configured")
            t.storagePath.convention(project.projectDir.resolve("build/moon-storage").absolutePath)
            t.dryRun.set(ext.dryRun)
        }

        // Root-only tasks
        if (project == project.rootProject) {
            project.tasks.register("createDockerNetwork", CreateDockerNetworkTask::class.java) { t ->
                t.setGroup("moon")
                t.setDescription("Create docker network if it does not exist")
                t.networkName.set(ext.networkName)
            }

            project.tasks.register("ensureSystemUserAndGroup", EnsureHostUserGroupTask::class.java) { t ->
                t.setGroup("moon")
                t.setDescription("Ensure host system user and group for moon are present")
                t.userName.set(ext.userName)
                t.groupName.set(ext.groupName)
                t.uid.set(ext.uid)
                t.gid.set(ext.gid)
                t.skipIfNoPrivs.set(ext.skipIfNoPrivs)
                t.dryRun.set(ext.dryRun)
            }

            // Optionally wire into buildEnvironment if present and requested
            project.afterEvaluate {
                if (ext.integrateWithBuildEnvironment.getOrElse(false)) {
                    val be = project.tasks.findByName("buildEnvironment")
                    if (be != null) {
                        be.dependsOn("createDockerNetwork", "ensureSystemUserAndGroup")
                    }
                }
            }
        }
    }
}
