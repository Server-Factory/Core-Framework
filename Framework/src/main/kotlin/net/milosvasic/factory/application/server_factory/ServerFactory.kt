package net.milosvasic.factory.application.server_factory

import net.milosvasic.factory.*
import net.milosvasic.factory.application.DefaultInitializationHandler
import net.milosvasic.factory.behavior.Behavior
import net.milosvasic.factory.common.Application
import net.milosvasic.factory.common.busy.Busy
import net.milosvasic.factory.common.busy.BusyDelegation
import net.milosvasic.factory.common.busy.BusyException
import net.milosvasic.factory.common.busy.BusyWorker
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.initialization.Initializer
import net.milosvasic.factory.common.initialization.Termination
import net.milosvasic.factory.common.obtain.Obtain
import net.milosvasic.factory.component.database.manager.DatabaseManager
import net.milosvasic.factory.component.docker.Docker
import net.milosvasic.factory.component.docker.DockerInitializationFlowCallback
import net.milosvasic.factory.component.installer.Installer
import net.milosvasic.factory.component.installer.step.InstallationStepType
import net.milosvasic.factory.component.installer.step.deploy.Deploy
import net.milosvasic.factory.configuration.*
import net.milosvasic.factory.configuration.builder.SoftwareBuilder
import net.milosvasic.factory.configuration.builder.SoftwareConfigurationBuilder
import net.milosvasic.factory.configuration.builder.SoftwareConfigurationItemBuilder
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.execution.TaskExecutor
import net.milosvasic.factory.execution.flow.FlowBuilder
import net.milosvasic.factory.execution.flow.callback.DieOnFailureCallback
import net.milosvasic.factory.execution.flow.callback.FlowCallback
import net.milosvasic.factory.execution.flow.callback.TerminationCallback
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.execution.flow.implementation.InstallationFlow
import net.milosvasic.factory.execution.flow.implementation.ObtainableTerminalCommand
import net.milosvasic.factory.execution.flow.implementation.initialization.InitializationFlow
import net.milosvasic.factory.firewall.DisableIptablesForMdns
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.operation.OperationResultListener
import net.milosvasic.factory.platform.*
import net.milosvasic.factory.proxy.ProxyInstallation
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.*
import java.awt.HeadlessException
import java.awt.MouseInfo
import java.awt.Robot
import java.util.concurrent.ConcurrentLinkedQueue


abstract class ServerFactory(private val builder: ServerFactoryBuilder) : Application, BusyDelegation {

    private var featureDatabase = true
    protected lateinit var installer: Installer
    protected var configuration: Configuration? = null

    private val busy = Busy()
    private var runStartedAt = 0L
    private val executor = TaskExecutor.instantiate(5)
    private val terminators = ConcurrentLinkedQueue<Termination>()
    private var configurations = mutableListOf<SoftwareConfiguration>()
    private val terminationOperation = ServerFactoryTerminationOperation()
    private val subscribers = ConcurrentLinkedQueue<OperationResultListener>()
    private val initializationOperation = ServerFactoryInitializationOperation()

    private val configurationManagerInitCallback = object : FlowCallback {
        override fun onFinish(success: Boolean) {

            if (success) {
                log.i("Configuration manager is initialized")

                configuration = ConfigurationManager.getConfiguration()
                if (configuration == null) {

                    throw IllegalStateException("Configuration is null")
                }

                val ssh = getConnection()
                installer = instantiateInstaller(ssh)
                terminators.add(installer)

                val callback = object : FlowCallback {
                    override fun onFinish(success: Boolean) {
                        if (success) {
                            try {

                                ConfigurationManager.load(ssh.getRemoteOS())
                                configuration?.let { config ->

                                    val configurationItems = ConfigurationManager.getConfigurationItems()
                                    configurations.addAll(configurationItems)
                                    config.name?.let { name ->

                                        log.v(name)
                                    }
                                }
                                notifyInit()
                            } catch (e: IllegalStateException) {

                                notifyInit(e)
                            } catch (e: IllegalArgumentException) {

                                notifyInit(e)
                            }
                        } else {

                            val e = IllegalStateException("Initialization failure")
                            notifyInit(e)
                        }
                    }
                }

                getCommandFlow(ssh, DieOnFailureCallback())
                    .onFinish(callback)
                    .run()
            } else {

                val error = IllegalStateException("Configuration manager was not initialized")
                notifyInit(error)
            }
        }
    }

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    override fun initialize() {

        checkInitialized()
        busy()
        try {

            tag = getLogTag()
            builder.getLogger()?.let {

                compositeLogger.addLogger(it)
            }
            featureDatabase = builder.getFeatureDatabase()
            try {

                ConfigurationManager.setConfigurationRecipe(builder.getRecipe())
                ConfigurationManager.setConfigurationFactory(getConfigurationFactory())
                ConfigurationManager.setInstallationLocation(builder.getInstallationLocation())

                val handler = DefaultInitializationHandler()
                try {

                    InitializationFlow()
                        .width(ConfigurationManager)
                        .handler(handler)
                        .onFinish(configurationManagerInitCallback)
                        .run()

                } catch (e: BusyException) {

                    notifyInit(e)
                }
            } catch (e: IllegalArgumentException) {

                notifyInit(e)
            } catch (e: IllegalStateException) {

                notifyInit(e)
            } catch (e: RuntimeException) {

                notifyInit(e)
            }
        } catch (e: IllegalArgumentException) {

            notifyInit(e)
        }
    }

    @Throws(IllegalStateException::class)
    override fun terminate() {

        checkNotInitialized()
        if (!busy.isBusy()) {
            throw IllegalStateException("Server factory is not running")
        }
        try {
            terminators.forEach {
                it.terminate()
            }
            configuration = null
            configurations.clear()
            notifyTerm()
        } catch (e: IllegalStateException) {
            notifyTerm(e)
        }
    }

    @Synchronized
    override fun isInitialized(): Boolean {
        try {

            ConfigurationManager.getConfiguration()
            return true
        } catch (e: IllegalStateException) {
            log.w(e)
        }
        return false
    }

    @Throws(IllegalStateException::class)
    override fun run() {

        checkNotInitialized()
        busy()
        if (configuration == null) {
            throw IllegalStateException("Configuration is null")
        }
        log.i("Server factory started")
        runStartedAt = System.currentTimeMillis()
        try {

            val ssh = getConnection()
            val docker = instantiateDocker(ssh)
            val databaseManager = getDatabaseManager(ssh)

            terminators.add(docker)
            terminators.add(databaseManager)

            val terminationFlow = getTerminationFlow(ssh)
            val dockerFlow = if (featureDatabase) {

                val loadDbsFlow = databaseManager.loadDatabasesFlow().connect(terminationFlow)
                getDockerFlow(docker, loadDbsFlow)
            } else {

                getDockerFlow(docker, terminationFlow)
            }
            val dockerInitFlow = getDockerInitFlow(docker, dockerFlow)
            val installationFlow = getInstallationFlow(installer, dockerInitFlow) ?: dockerInitFlow
            val initializers = listOf<Initializer>(databaseManager)
            val initFlow = getInitializationFlow(initializers, installationFlow)

            initFlow.run()
        } catch (e: IllegalArgumentException) {

            fail(e)
        } catch (e: IllegalStateException) {

            fail(e)
        }
    }

    override fun onStop() {

        val duration = getDuration()
        log.i("Server factory finished in: $duration")
    }

    @Synchronized
    @Throws(BusyException::class)
    override fun busy() {

        BusyWorker.busy(busy)
    }

    @Synchronized
    override fun free() {

        BusyWorker.free(busy)
    }

    fun isBusy() = busy.isBusy()

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun checkInitialized() {

        if (ConfigurationManager.isInitialized()) {
            throw IllegalStateException("Server factory has been already initialized")
        }
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun checkNotInitialized() {

        if (!ConfigurationManager.isInitialized()) {
            throw IllegalStateException("Server factory has not been initialized")
        }
    }

    override fun subscribe(what: OperationResultListener) {

        subscribers.add(what)
    }

    override fun unsubscribe(what: OperationResultListener) {

        subscribers.remove(what)
    }

    @Synchronized
    override fun notify(data: OperationResult) {

        val iterator = subscribers.iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next()
            listener.onOperationPerformed(data)
        }
    }

    protected abstract fun getConfigurationFactory(): ConfigurationFactory<*>

    @Throws(IllegalArgumentException::class)
    protected fun getConnection() = ConfigurationManager.getConnection()

    protected open fun getLogTag() = tag

    protected open fun instantiateDocker(ssh: Connection) = Docker(ssh)

    protected open fun instantiateInstaller(ssh: Connection) = Installer(ssh)

    protected open fun getHostInfoCommand(): TerminalCommand = HostInfoCommand()

    protected open fun getDatabaseManager(ssh: Connection) = DatabaseManager(ssh)

    protected open fun getHostInfoDataHandler(os: OperatingSystem) = HostInfoDataHandler(os)

    protected open fun getHostNameSetCommand(hostname: String): TerminalCommand = HostNameSetCommand(hostname)

    @Throws(IllegalArgumentException::class)
    protected open fun getCoreUtilsInstallationDependencies(): SoftwareConfiguration {

        val wget = InstallationStepDefinition(InstallationStepType.PACKAGES, value = "wget")
        val bzip2 = InstallationStepDefinition(InstallationStepType.PACKAGES, value = "bzip2")
        val selinuxPackages = "selinux-basics, selinux-policy-default, auditd"
        val selinux = InstallationStepDefinition(InstallationStepType.PACKAGES, value = selinuxPackages)

        val softwareConfigurationItemBuilder = SoftwareConfigurationItemBuilder()
            .setName(Deploy.SOFTWARE_CONFIGURATION_NAME)
            .setVersion(BuildInfo.version)
            .addInstallationStep(Platform.CENTOS, bzip2)
            .addInstallationStep(Platform.CENTOS, wget)
            .addInstallationStep(Platform.UBUNTU, bzip2)
            .addInstallationStep(Platform.UBUNTU, selinux)

        val softwareBuilder = SoftwareBuilder()
            .addItem(softwareConfigurationItemBuilder)

        val builder = SoftwareConfigurationBuilder()
            .setEnabled(true)
            .setConfiguration(Deploy.SOFTWARE_CONFIGURATION_NAME)
            .setPlatform(Platform.CENTOS)
            .setSoftware(softwareBuilder)

        return builder.build()
    }

    protected open fun getCoreUtilsInstallerInitializationFlow(): FlowBuilder<*, *, *> {

        return InitializationFlow().width(installer)
    }

    private fun notifyInit() {

        free()
        val result = OperationResult(initializationOperation, true)
        notify(result)
        keepAlive()
    }

    @Synchronized
    private fun notifyInit(e: Exception) {

        free()
        log.e(e)
        val result = OperationResult(initializationOperation, false)
        notify(result)
    }

    private fun notifyTerm() {

        free()
        onStop()
        val result = OperationResult(terminationOperation, true)
        notify(result)
    }

    @Synchronized
    private fun notifyTerm(e: Exception) {

        free()
        onStop()
        log.e(e)
        val result = OperationResult(terminationOperation, false)
        notify(result)
    }

    private fun getInstallationFlow(installer: Installer, dockerInitFlow: InitializationFlow): InstallationFlow? {

        val items = getConfigurationItems(SoftwareConfigurationType.SOFTWARE)
        if (items.isEmpty()) {
            return null
        }
        val installFlow = InstallationFlow(installer)
        val dieCallback = DieOnFailureCallback()
        items.forEach {

            installFlow.width(it)
        }
        return installFlow
            .connect(dockerInitFlow)
            .onFinish(dieCallback)
    }

    private fun getDockerFlow(docker: Docker, terminationFlow: FlowBuilder<*, *, *>): InstallationFlow {

        val dockerFlow = InstallationFlow(docker)
        val items = getConfigurationItems(SoftwareConfigurationType.DOCKER)
        items.forEach { softwareConfiguration ->
            softwareConfiguration.software?.forEach { software ->

                val configuration = SoftwareConfiguration(

                    softwareConfiguration.definition,
                    softwareConfiguration.uses,
                    softwareConfiguration.overrides,
                    softwareConfiguration.configuration,
                    softwareConfiguration.variables,
                    mutableListOf(software),
                    softwareConfiguration.includes
                )
                val platformName = getConnection().getRemoteOS().getPlatform().platformName
                configuration.setPlatform(platformName)
                dockerFlow.width(configuration)
            }
        }

        dockerFlow.connect(terminationFlow)
        return dockerFlow
    }

    protected open fun getTerminationFlow(connection: Connection): FlowBuilder<*, *, *> {

        return CommandFlow()
            .width(connection.getTerminal())
            .perform(EchoCommand("Finishing"))
            .onFinish(TerminationCallback(this))
    }

    protected open fun getCoreUtilsDeploymentFlow(

        what: String,
        where: String,
        ssh: Connection

    ) = Deploy(what, where, true)
        .setConnection(ssh)
        .toCommandFlow()

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected open fun getProxyInstallationFlow(ssh: Connection): CommandFlow {

        val conf = ConfigurationManager.getConfiguration()
        val proxy = conf.getProxy()

        return ProxyInstallation(proxy)
            .setConnection(ssh)
            .toCommandFlow()
    }

    protected open fun getIpAddressObtainCommand(os: OperatingSystem) =
        object : Obtain<TerminalCommand> {

            val hostname = getHostname()
            override fun obtain() = IpAddressObtainCommand(hostname)
        }

    private fun getDockerInitFlow(docker: Docker, dockerFlow: InstallationFlow): InitializationFlow {

        val initCallback = DockerInitializationFlowCallback()
        return InitializationFlow()
            .width(docker)
            .connect(dockerFlow)
            .onFinish(initCallback)
    }

    @Throws(IllegalArgumentException::class)
    private fun getInitializationFlow(
        initializers: List<Initializer>,
        nextFlow: FlowBuilder<*, *, *>

    ): InitializationFlow {

        if (initializers.isEmpty()) {

            throw IllegalArgumentException("Initializers are not provided")
        }

        val flow = InitializationFlow()
        initializers.forEach {
            flow.width(it)
        }
        val dieCallback = DieOnFailureCallback()
        return flow.connect(nextFlow).onFinish(dieCallback)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun getCommandFlow(ssh: Connection, dieCallback: DieOnFailureCallback): CommandFlow {

        val os = ssh.getRemoteOS()
        val hostname = getHostname()
        val terminal = ssh.getTerminal()
        val host = ssh.getRemote().getHost(preferIpAddress = false)
        val pingCommand = PingCommand(host)
        val hostNameCommand = HostNameCommand()
        val hostInfoCommand = getHostInfoCommand()
        val testCommand = EchoCommand("Hello")

        /*
         * {
         *  "type": "deploy",
         *  "value": "{{SYSTEM.HOME}}/Core/Utils:{{SERVER.UTILS_HOME}}"
         * }
         */
        val systemHomePath = PathBuilder()
            .addContext(Context.System)
            .setKey(Key.Home)
            .build()

        val systemHome = Variable.get(systemHomePath)

        val what = FilePathBuilder()
            .addContext(systemHome)
            .addContext(Commands.DIRECTORY_CORE)
            .addContext(Commands.DIRECTORY_UTILS)
            .build()

        val whereRootPath = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.Home)
            .build()

        val whereRoot = Variable.get(whereRootPath)

        val where = FilePathBuilder()
            .addContext(whereRoot)
            .addContext(Commands.DIRECTORY_UTILS)
            .build()

        val coreUtilsDeployment = getCoreUtilsDeploymentFlow(what, where, ssh)
        if (hostname != String.EMPTY) {

            coreUtilsDeployment.perform(
                getHostNameSetCommand(hostname),
                HostNameDataHandler(os, hostname)
            )
        }

        val proxyInstallationFlow = getProxyInstallationFlow(ssh)

        val installationFlow = getCoreUtilsInstallationFlow()
        val installerInitFlow = getCoreUtilsInstallerInitializationFlow()

        val flow = CommandFlow()
            .width(terminal)
            .perform(pingCommand)
            .width(ssh)
            .perform(hostInfoCommand, getHostInfoDataHandler(os))
            .perform(hostNameCommand, HostNameDataHandler(os))

        val behavior = Behavior()
        val behaviorGetIp = behavior.behaviorGetIp()
        val behaviorDisableIpTablesForMdns = behavior.behaviorDisableIptablesForMdns()

        log.v("Behavior: GET_IP=$behaviorGetIp")
        log.v("Behavior: DISABLE_IPTABLES_FOR_MDNS=$behaviorDisableIpTablesForMdns")

        if (behaviorGetIp) {

            val getIpCommand = getIpAddressObtainCommand(os)
            val ipAddressHandler = HostIpAddressDataHandler(ssh.getRemote())
            val getIpObtainableCommand = ObtainableTerminalCommand(getIpCommand, ipAddressHandler)

            flow
                .width(terminal)
                .perform(getIpObtainableCommand)
        }

        flow
            .width(ssh)
            .perform(testCommand)
            .connect(installerInitFlow)
            .connect(installationFlow)
            .connect(coreUtilsDeployment)

        if (behaviorDisableIpTablesForMdns) {

            val disableCommand = DisableIptablesForMdns()

            val disableFlow = CommandFlow()
                .width(ssh)
                .perform(disableCommand)

            flow.connect(disableFlow)
        }

        return flow
            .connect(proxyInstallationFlow)
            .onFinish(dieCallback)
    }

    private fun getCoreUtilsInstallationFlow(): FlowBuilder<*, *, *> {

        val installationFlow = InstallationFlow(installer)
        val coreUtilsDeploymentDependencies = getCoreUtilsInstallationDependencies()

        installationFlow.width(coreUtilsDeploymentDependencies)
        installationFlow.onFinish(DieOnFailureCallback())

        return installationFlow
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun getHostname(): String {

        val path = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.Hostname)
            .build()

        val hostname = Variable.get(path)
        if (hostname == String.EMPTY) {

            throw IllegalArgumentException("Empty hostname obtained for the server")
        }
        return hostname
    }

    private fun getConfigurationItems(type: SoftwareConfigurationType): MutableList<SoftwareConfiguration> {

        val typeDocker = SoftwareConfigurationType.DOCKER
        val items = mutableListOf<SoftwareConfiguration>()
        configurations.forEach { item ->
            item.software?.let { softwareItems ->
                softwareItems.forEach { softwareItem ->
                    when (type) {
                        typeDocker -> {
                            if (softwareItem.hasInstallationSteps(type.label)) {
                                if (!items.contains(item)) {
                                    items.add(item)
                                }
                            }
                        }
                        else -> {
                            if (!softwareItem.hasInstallationSteps(typeDocker.label)) {
                                if (!items.contains(item)) {
                                    items.add(item)
                                }
                            }
                        }
                    }
                }
            }
        }
        return items
    }

    private fun getDuration(): String {

        val duration = System.currentTimeMillis() - runStartedAt
        val seconds = (duration / 1000).toInt() % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60)) % 24

        val minutesFormatted = if (minutes == 0L) {

            if (hours == 0L) {
                ""
            } else {
                String.format("%02d", minutes) + " min. : "
            }
        } else {

            String.format("%02d", minutes) + " min. : "
        }

        val hoursFormatted = if (hours == 0L) {
            ""
        } else {
            String.format("%02d", hours) + " hr. : "
        }

        val secondsFormatted = String.format("%02d", seconds) + " sec."

        return "$hoursFormatted$minutesFormatted$secondsFormatted"
    }

    private fun keepAlive() = executor.execute {

        log.i("Keep alive: START")
        var count = 0
        val robot = Robot()
        while (isInitialized()) {

            count++
            try {

                val pointerInfo = MouseInfo.getPointerInfo()
                val location = pointerInfo.location
                val x = location.getX().toInt() + 1
                val y = location.getY().toInt() + 1
                robot.delay(60 * 1000)
                if (isInitialized()) {

                    robot.mouseMove(x, y)
                    log.v("Keep alive, count: $count")
                }
            } catch (e: HeadlessException) {

                log.e(e)
            }
        }
    }
}