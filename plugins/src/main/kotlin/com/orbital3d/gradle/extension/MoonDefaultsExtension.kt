package com.orbital3d.gradle.extension

import com.orbital3d.gradle.api.PortProvider

/**
 * Root-level extension to allow the root project to provide default behaviour for the plugin.
 * Example in root build.gradle.kts:
 *
 *   extensions.create("moonDefaults", MoonDefaultsExtension::class.java).apply {
 *       portProvider = MyCustomProvider()
 *   }
 */
open class MoonDefaultsExtension {
    var portProvider: PortProvider? = null
}
