package net.milosvasic.factory.test.implementation

import net.milosvasic.factory.BuildInfo
import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.application.server_factory.ServerFactory
import net.milosvasic.factory.application.server_factory.ServerFactoryBuilder
import net.milosvasic.factory.common.obtain.Obtain
import net.milosvasic.factory.component.docker.Docker
import net.milosvasic.factory.component.installer.Installer
import net.milosvasic.factory.component.installer.step.InstallationStepType
import net.milosvasic.factory.component.installer.step.deploy.Deploy
import net.milosvasic.factory.configuration.ConfigurationFactory
import net.milosvasic.factory.configuration.InstallationStepDefinition
import net.milosvasic.factory.configuration.SoftwareConfiguration
import net.milosvasic.factory.configuration.builder.SoftwareBuilder
import net.milosvasic.factory.configuration.builder.SoftwareConfigurationBuilder
import net.milosvasic.factory.configuration.builder.SoftwareConfigurationItemBuilder
import net.milosvasic.factory.execution.flow.FlowBuilder
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.execution.flow.implementation.initialization.InitializationFlow
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.platform.HostInfoDataHandler
import net.milosvasic.factory.platform.OperatingSystem
import net.milosvasic.factory.platform.Platform
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.EchoCommand
import net.milosvasic.factory.terminal.command.UnameCommand

class StubServerFactory(builder: ServerFactoryBuilder) : ServerFactory(builder) {

    private val recipeRegistrar = StubRecipeRegistrar()

    override fun getHostInfoCommand() = UnameCommand()

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun instantiateInstaller(ssh: Connection): Installer {

        val installer = super.instantiateInstaller(ssh)
        val stubPackageManager = StubPackageManager(getConnection())

        installer.addSupportedPackageManager(stubPackageManager)
        installer.addProcessingRecipesRegistrar(recipeRegistrar)
        return installer
    }

    override fun getConfigurationFactory(): ConfigurationFactory<*> {

        return StubServerConfigurationFactory()
    }

    override fun instantiateDocker(ssh: Connection): Docker {

        val docker = super.instantiateDocker(ssh)
        docker.addProcessingRecipesRegistrar(recipeRegistrar)
        return docker
    }

    override fun getDatabaseManager(ssh: Connection) = StubDatabaseManager(ssh)

    override fun getHostNameSetCommand(hostname: String) = EchoCommand(hostname)

    override fun getHostInfoDataHandler(os: OperatingSystem) =
        object : HostInfoDataHandler(getConnection().getRemoteOS()) {

            override fun onData(data: OperationResult?) = os.setPlatform(Platform.CENTOS)
        }

    override fun getCoreUtilsDeploymentFlow(

        what: String,
        where: String,
        ssh: Connection

    ) = CommandFlow()
        .width(ssh)
        .perform(EchoCommand("Core utils stub deployment: $what -> $where"))

    override fun getProxyInstallationFlow(ssh: Connection) = CommandFlow()
        .width(ssh)
        .perform(EchoCommand("Proxy stub installation"))

    override fun getIpAddressObtainCommand(os: OperatingSystem) =
        object : Obtain<TerminalCommand> {

            override fun obtain() = EchoCommand(LOCALHOST)
        }

    override fun getCoreUtilsInstallationDependencies(): SoftwareConfiguration {

        val bzip2 = InstallationStepDefinition(InstallationStepType.COMMAND, value = "echo \"STUB\"")

        val softwareConfigurationItemBuilder = SoftwareConfigurationItemBuilder()
            .setName(Deploy.SOFTWARE_CONFIGURATION_NAME)
            .setVersion(BuildInfo.version)
            .addInstallationStep(Platform.CENTOS, bzip2)
            .addInstallationStep(Platform.UBUNTU, bzip2)

        val softwareBuilder = SoftwareBuilder()
            .addItem(softwareConfigurationItemBuilder)

        val builder = SoftwareConfigurationBuilder()
            .setEnabled(true)
            .setConfiguration(Deploy.SOFTWARE_CONFIGURATION_NAME)
            .setPlatform(Platform.CENTOS)
            .setSoftware(softwareBuilder)

        return builder.build()
    }

    override fun getCoreUtilsInstallerInitializationFlow(): FlowBuilder<*, *, *> {

        return InitializationFlow().width(installer)
    }
}