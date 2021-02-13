package net.milosvasic.factory.configuration.variable

interface Key {

    fun key(): String

    companion object {

        val Home = object : Key {
            override fun key() = "HOME"
        }

        val Account = object : Key {
            override fun key() = "ACCOUNT"
        }

        val ServerHome = object : Key {
            override fun key() = "SERVER_HOME"
        }

        val DockerHome = object : Key {
            override fun key() = "DOCKER_HOME"
        }

        val DockerComposePath = object : Key {
            override fun key() = "DOCKER_COMPOSE_PATH"
        }

        val Type = object : Key {
            override fun key() = "TYPE"
        }

        val Name = object : Key {
            override fun key() = "NAME"
        }

        val Host = object : Key {
            override fun key() = "HOST"
        }

        val Port = object : Key {
            override fun key() = "PORT"
        }

        val User = object : Key {
            override fun key() = "USER"
        }

        val GetIp = object : Key {
            override fun key() = "GET_IP"
        }

        val Hostname = object : Key {
            override fun key() = "HOSTNAME"
        }

        val Password = object : Key {
            override fun key() = "PASSWORD"
        }

        val SelfSigned = object : Key {
            override fun key() = "SELF_SIGNED"
        }

        val CaEndpoint = object : Key {
            override fun key() = "CA_ENDPOINT"
        }

        val RefreshFrequency = object : Key {
            override fun key() = "REFRESH_FREQUENCY"
        }

        val Passphrase = object : Key {
            override fun key() = "PASSPHRASE"
        }

        val Certificates = object : Key {
            override fun key() = "CERTIFICATES"
        }

        val CaBundle = object : Key {
            override fun key() = "CA_BUNDLE"
        }

        val PortExposed = object : Key {
            override fun key() = "PORT_EXPOSED"
        }

        val RebootAllowed = object : Key {
            override fun key() = "REBOOT_ALLOWED"
        }

        val City = object : Key {
            override fun key() = "CITY"
        }

        val Country = object : Key {
            override fun key() = "COUNTRY"
        }

        val Province = object : Key {
            override fun key() = "PROVINCE"
        }

        val Department = object : Key {
            override fun key() = "DEPARTMENT"
        }

        val Organisation = object : Key {
            override fun key() = "ORGANISATION"
        }
    }
}