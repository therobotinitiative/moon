package com.orbital3d.gradle.api

/**
 * Interface to provide a default port.
 */
interface PortProvider {
    fun defaultPort(projectName: String): Int
}
