package com.orbital3d.gradle.extension

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class MoonExtension @Inject constructor(
    objects: ObjectFactory
) {
    val port: Property<Int> = objects.property(Int::class.java)
    val user: Property<String> = objects.property(String::class.java)
    val database: Property<String> = objects.property(String::class.java)
    val storagePath: Property<String> = objects.property(String::class.java)
    val imageName: Property<String> = objects.property(String::class.java)
    val imageVersion: Property<String> = objects.property(String::class.java)
    val uid: Property<String> = objects.property(String::class.java)
    val gid: Property<String> = objects.property(String::class.java)
}
