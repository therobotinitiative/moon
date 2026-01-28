package com.orbital3d.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CheckProperties : DefaultTask() {
	@get:Input
	abstract val port: Property<String>

	@get:Input
	abstract val user: Property<String>

	@get:Input
	abstract val database: Property<String>

	@get:Input
	abstract val storagePath: Property<String>

	@get:Input
	abstract val imageName: Property<String>

	@get:Input
	abstract val imageVersion: Property<String>

	@TaskAction
	fun checkProperties() {
		println("Port: ${port.get()}")
		println("User: ${user.get()}")
		println("Database: ${database.get()}")
		println("Storage Path: ${storagePath.get()}")
		println("Image name: ${imageName.get()}")
		println("version: ${imageVersion.get()}")
	}
}