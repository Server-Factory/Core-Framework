package net.milosvasic.factory.configuration.variable

import net.milosvasic.factory.component.docker.DockerCommand
import net.milosvasic.factory.terminal.command.Commands

interface Context {

    fun context(): String

    companion object {

        val System = object : Context {
            override fun context() = "SYSTEM"
        }

        val Proxy = object : Context {
            override fun context() = Commands.DIRECTORY_PROXY.toUpperCase()
        }

        val Installation = object : Context {
            override fun context() = "INSTALLATION"
        }

        val Server = object : Context {
            override fun context() = Commands.DIRECTORY_SERVER.toUpperCase()
        }

        val Behavior = object : Context {
            override fun context() = "BEHAVIOR"
        }

        val Ports = object : Context {
            override fun context() = "PORTS"
        }

        val Service = object : Context {
            override fun context() = "SERVICE"
        }

        val Database = object : Context {
            override fun context() = "DATABASE"
        }

        val Certification = object : Context {
            override fun context() = "CERTIFICATION"
        }

        val Docker = object : Context {
            override fun context() = DockerCommand.DOCKER.obtain().toUpperCase()
        }
    }
}