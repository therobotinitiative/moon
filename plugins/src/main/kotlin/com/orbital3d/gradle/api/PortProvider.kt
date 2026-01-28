package com.orbital3d.gradle.api

interface PortProvider {
    fun defaultPort(projectName: String): Int
}
