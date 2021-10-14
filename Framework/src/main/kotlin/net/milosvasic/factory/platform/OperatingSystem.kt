package net.milosvasic.factory.platform

import net.milosvasic.factory.EMPTY

data class OperatingSystem(
    private var name: String = "System unknown",
    private var platform: Platform = Platform.UNKNOWN,
    private var architecture: Architecture = Architecture.UNKNOWN,
    private var hostname: String = String.EMPTY
) {

    companion object {

        @Throws(IllegalArgumentException::class, NullPointerException::class, SecurityException::class)
        fun getHostOperatingSystem(): OperatingSystem {

            val platform = when {

                isMacOS() -> Platform.MAC_OS
                isLinux() -> Platform.LINUX
                isWindows() -> Platform.WINDOWS
                else -> Platform.UNKNOWN
            }
            return OperatingSystem(

                name = "Host",
                platform = platform,
                architecture = Architecture.UNKNOWN,
                hostname = "Unknown"
            )
        }

        @Throws(IllegalArgumentException::class, NullPointerException::class, SecurityException::class)
        private fun isMacOS() = getOS().contains("mac")

        @Throws(IllegalArgumentException::class, NullPointerException::class, SecurityException::class)
        private fun isLinux() = getOS().contains("mac")

        @Throws(IllegalArgumentException::class, NullPointerException::class, SecurityException::class)
        private fun isWindows() = getOS().contains("mac")

        @Throws(IllegalArgumentException::class, NullPointerException::class, SecurityException::class)
        private fun getOS(): String {

            return System.getProperty("os.name").lowercase()
        }
    }

    @Throws(IllegalArgumentException::class)
    fun setHostname(data: String) {

        val validator = HostNameValidator()
        if (validator.validate(data)) {

            hostname = data
        } else {

            throw IllegalArgumentException("Invalid hostname: $data")
        }
    }

    fun parseAndSetSystemInfo(data: String) {

        val osLineString = "Operating System:"
        val archLineString = "Architecture:"
        val lines = data.split("\n")
        lines.forEach {
            if (it.contains(osLineString)) {
                name = it.replace(osLineString, "").trim()
                if (name.lowercase().contains(Platform.CENTOS.platformName.lowercase())) {

                    platform = if (name.lowercase().contains("linux 8")) {
                        Platform.CENTOS
                    } else {
                        Platform.CENTOS_7
                    }
                }
                if (name.lowercase().contains(Platform.FEDORA.platformName.lowercase())) {

                    platform = if (name.lowercase().contains("server")) {
                        when {
                            name.lowercase().contains("30") -> {
                                Platform.FEDORA_SERVER_30
                            }
                            name.lowercase().contains("31") -> {
                                Platform.FEDORA_SERVER_31
                            }
                            name.lowercase().contains("32") -> {
                                Platform.FEDORA_SERVER_32
                            }
                            name.lowercase().contains("33") -> {
                                Platform.FEDORA_SERVER_33
                            }
                            else -> {
                                Platform.FEDORA_SERVER
                            }
                        }
                    } else {

                        when {
                            name.lowercase().contains("30") -> {
                                Platform.FEDORA_30
                            }
                            name.lowercase().contains("31") -> {
                                Platform.FEDORA_31
                            }
                            name.lowercase().contains("32") -> {
                                Platform.FEDORA_32
                            }
                            name.lowercase().contains("33") -> {
                                Platform.FEDORA_33
                            }
                            else -> {
                                Platform.FEDORA
                            }
                        }
                    }
                }
                if (name.lowercase().contains(Platform.REDHAT.platformName.lowercase())) {
                    platform = Platform.REDHAT
                }
                if (name.lowercase().contains(Platform.UBUNTU.platformName.lowercase())) {
                    platform = Platform.UBUNTU
                }
                if (name.lowercase().contains(Platform.DEBIAN.platformName.lowercase())) {
                    platform = Platform.DEBIAN
                }
            }
            if (it.contains(archLineString)) {
                val arch = it.replace(archLineString, "")
                    .replace("-", "")
                    .replace("_", "")
                    .trim()
                    .lowercase()

                when {
                    arch.startsWith("x8664") -> {
                        architecture = Architecture.X86_64
                    }
                    arch.startsWith(Architecture.X86_64.arch) -> {
                        architecture = Architecture.X86_64
                    }
                    arch.startsWith(Architecture.ARMHF.arch) -> {
                        architecture = Architecture.ARMHF
                    }
                    arch.startsWith(Architecture.ARM64.arch) -> {
                        architecture = Architecture.ARM64
                    }
                    arch.startsWith(Architecture.PPC64EL.arch) -> {
                        architecture = Architecture.PPC64EL
                    }
                    arch.startsWith(Architecture.S390X.arch) -> {
                        architecture = Architecture.S390X
                    }
                }
            }
        }
    }

    fun getName() = name

    fun getPlatform() = platform

    fun setPlatform(type: Platform) {

        this.platform = type
    }

    fun getHostname() = hostname

    fun getArchitecture() = architecture
}