package com.orbital3d.gradle.api

/**
 * Default port number provider. This implementation can be overwriten in Moon Modules.
 * Moon project name is associated with default port.
 */
class DefaultPortProvider : PortProvider {
    override fun defaultPort(projectName: String): Int {
        return when (projectName) {
            "cellblock" -> 3306
            "fantti" -> 5432
            "puppu" -> 5100
            "redis" -> 6379
            else -> -1
        }
    }
}
