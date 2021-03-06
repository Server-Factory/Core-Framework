package net.milosvasic.factory.component.packaging

import net.milosvasic.factory.remote.Connection

class Apt(entryPoint: Connection) : PackageManager(entryPoint) {

    override val applicationBinaryName: String
        get() = "apt"

    override fun installCommand(): String {
        return "export DEBIAN_FRONTEND=noninteractive; " + super.installCommand() + " --fix-missing --allow-downgrades"
    }

    override fun uninstallCommand(): String {
        return "export DEBIAN_FRONTEND=noninteractive; $applicationBinaryName remove -y"
    }

    override fun groupInstallCommand(): String {
        return "export DEBIAN_FRONTEND=noninteractive; " + super.groupInstallCommand()
    }

    override fun groupUninstallCommand(): String {
        return "export DEBIAN_FRONTEND=noninteractive; " + super.groupUninstallCommand()
    }
}