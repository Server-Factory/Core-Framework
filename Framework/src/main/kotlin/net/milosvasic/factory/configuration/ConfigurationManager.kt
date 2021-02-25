package net.milosvasic.factory.configuration

import net.milosvasic.factory.DIRECTORY_DEFAULT_INSTALLATION_LOCATION
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.busy.Busy
import net.milosvasic.factory.common.busy.BusyDelegation
import net.milosvasic.factory.common.busy.BusyException
import net.milosvasic.factory.common.busy.BusyWorker
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.initialization.Initializer
import net.milosvasic.factory.component.docker.environment.DefaultEnvironmentVariablesFactory
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.configuration.definition.provider.DefinitionProvider
import net.milosvasic.factory.configuration.definition.provider.FilesystemDefinitionProvider
import net.milosvasic.factory.configuration.recipe.ConfigurationRecipe
import net.milosvasic.factory.configuration.recipe.FileConfigurationRecipe
import net.milosvasic.factory.configuration.recipe.RawJsonConfigurationRecipe
import net.milosvasic.factory.configuration.variable.*
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.operation.OperationResultListener
import net.milosvasic.factory.platform.HostIpAddressDataHandler
import net.milosvasic.factory.platform.OperatingSystem
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.ConnectionProvider
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.terminal.command.Commands
import net.milosvasic.factory.terminal.command.IpAddressObtainCommand
import net.milosvasic.factory.validation.JsonValidator
import net.milosvasic.factory.validation.networking.IPV4Validator
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

object ConfigurationManager : Initializer, BusyDelegation {

    private val busy = Busy()
    private var loaded = AtomicBoolean()
    private val loading = AtomicBoolean()
    private var configuration: Configuration? = null
    private var recipe: ConfigurationRecipe<*>? = null
    private lateinit var definitionProvider: DefinitionProvider
    private val connectionPool = mutableMapOf<String, Connection>()
    private var configurationFactory: ConfigurationFactory<*>? = null
    private var configurations = mutableListOf<SoftwareConfiguration>()
    private val subscribers = ConcurrentLinkedQueue<OperationResultListener>()
    private var installationLocation = DIRECTORY_DEFAULT_INSTALLATION_LOCATION
    private val initializationOperation = ConfigurationManagerInitializationOperation()

    private var connectionProvider: ConnectionProvider = object : ConnectionProvider {

        @Throws(IllegalArgumentException::class)
        override fun obtain(): Connection {
            configuration?.let { config ->

                val key = config.remote.toString()
                connectionPool[key]?.let {
                    return it
                }
                val connection = SSH(config.remote)
                connectionPool[key] = connection
                return connection
            }
            throw IllegalArgumentException("No valid configuration available for creating a connection")
        }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun initialize() {

        checkInitialized()
        busy()
        if (configurationFactory == null) {

            throw IllegalStateException("Configuration factory was not provided")
        }
        if (recipe == null) {

            throw IllegalStateException("Configuration recipe was not provided")
        }
        recipe?.let { rcp ->

            configuration = configurationFactory?.obtain(rcp)
            nullConfigurationCheck()
            configuration?.let {

                initializeSystemVariables(it)
                initializeDockerVariables(it)

                val callback = Runnable {

                    notifyInit()
                }
                initializeProxyVariables(it, callback)
            }
        }
    }

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun load(operatingSystem: OperatingSystem) {

        checkNotInitialized()
        nullConfigurationCheck()
        if (loaded.get()) {

            throw IllegalStateException("Configuration is already loaded")
        }
        if (loading.get()) {
            return
        }
        loading.set(true)
        configuration?.let { config ->
            config.enabled?.let { enabled ->
                if (!enabled) {

                    throw IllegalStateException("Configuration is not enabled")
                }
            }

            config.uses?.forEach { use ->

                log.v("Required definition dependency: $use")
                val definition = Definition.fromString(use)
                definitionProvider = FilesystemDefinitionProvider(config, operatingSystem)
                val loaded = definitionProvider.load(definition)
                configurations.addAll(loaded)
            }

            printVariableNode(config.variables)
        }
        loaded.set(true)
        loading.set(false)
    }

    @Throws(IllegalStateException::class)
    fun getConfiguration(): Configuration {

        checkNotInitialized()
        configuration?.let {
            return it
        }
        throw IllegalStateException("No configuration available")
    }

    @Synchronized
    override fun isInitialized(): Boolean {

        return configuration != null
    }

    @Synchronized
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setConfigurationRecipe(recipe: ConfigurationRecipe<*>) {

        checkInitialized()
        notLoadConfigurationCheck()
        when (recipe) {
            is FileConfigurationRecipe -> {

                val path = recipe.data.absolutePath
                val validator = ConfigurationPathValidator()
                validator.validate(path)
            }
            is RawJsonConfigurationRecipe -> {

                val json = recipe.data
                val validator = JsonValidator()
                validator.validate(json)
            }
            else -> {

                throw IllegalArgumentException("Unsupported recipe type: ${recipe::class.simpleName}")
            }
        }

        this.recipe = recipe
    }

    @Synchronized
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setConfigurationFactory(factory: ConfigurationFactory<*>) {

        checkInitialized()
        notLoadConfigurationCheck()
        configurationFactory = factory
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun checkInitialized() {

        if (isInitialized()) {
            throw IllegalStateException("Configuration manager has been already initialized")
        }
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun checkNotInitialized() {

        if (!isInitialized()) {
            throw IllegalStateException("Configuration manager has not been initialized")
        }
    }

    fun getConfigurationItems() = configurations

    fun setInstallationLocation(location: String) {

        installationLocation = location
    }

    fun setConnectionProvider(provider: ConnectionProvider) {

        connectionProvider = provider
    }

    @Throws(IllegalArgumentException::class)
    fun getConnection() = connectionProvider.obtain()

    @Synchronized
    @Throws(BusyException::class)
    override fun busy() {

        BusyWorker.busy(busy)
    }

    @Synchronized
    override fun free() {

        BusyWorker.free(busy)
    }

    @Synchronized
    override fun notify(data: OperationResult) {

        val iterator = subscribers.iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next()
            listener.onOperationPerformed(data)
        }
    }

    fun isBusy() = busy.isBusy()

    override fun subscribe(what: OperationResultListener) {

        subscribers.add(what)
    }

    override fun unsubscribe(what: OperationResultListener) {

        subscribers.remove(what)
    }

    private fun printVariableNode(variableNode: Node?, prefix: String = String.EMPTY) {

        val prefixEnd = "-> "
        variableNode?.let { node ->
            if (node.value != String.EMPTY) {
                val printablePrefix = if (prefix != String.EMPTY) {
                    " $prefix $prefixEnd"
                } else {
                    " "
                }
                node.value.let { value ->
                    val nodeValue = Variable.parse(value)
                    node.name.let { name ->
                        if (name != String.EMPTY) {
                            log.d("Configuration variable:$printablePrefix$name -> $nodeValue")
                        }
                    }
                }
            }
            node.children.forEach { child ->
                var nextPrefix = prefix
                if (nextPrefix != String.EMPTY && !nextPrefix.endsWith(prefixEnd)) {
                    nextPrefix += " $prefixEnd"
                }
                nextPrefix += node.name
                printVariableNode(child, nextPrefix)
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun nullConfigurationCheck() {

        if (configuration == null) {
            throw IllegalStateException("Configuration was not initialised")
        }
    }

    @Throws(IllegalStateException::class)
    private fun notLoadConfigurationCheck() {

        if (loaded.get()) {
            throw IllegalStateException("Configuration has been already loaded")
        }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun initializeSystemVariables(config: Configuration) {

        var node: Node? = null
        config.variables?.let {
            node = it
        }
        if (node == null) {

            node = Node()
            config.variables = node
        }

        val keyHome = Key.Home
        val ctxSystem = Context.System
        val ctxInstallation = Context.Installation

        val pathSystemHome = PathBuilder()
            .addContext(ctxSystem)
            .setKey(keyHome)
            .build()

        val pathSystemInstallationHome = PathBuilder()
            .addContext(ctxSystem)
            .addContext(ctxInstallation)
            .setKey(keyHome)
            .build()

        val systemHomeVariable = checkAndGetVariable(pathSystemHome)
        val systemInstallationHomeVariable = checkAndGetVariable(pathSystemInstallationHome)

        val systemVariables = mutableListOf<Node>()
        if (systemHomeVariable.isEmpty()) {

            val systemHome = getHomeDirectory()
            val systemHomeNode = Node(name = keyHome.key(), value = systemHome.absolutePath)
            systemVariables.add(systemHomeNode)
        }
        if (systemInstallationHomeVariable.isEmpty()) {

            val installationHomeNode = Node(name = keyHome.key(), value = installationLocation)
            val installationVariables = mutableListOf(installationHomeNode)
            val installationNode = Node(name = ctxInstallation.context(), children = installationVariables)
            systemVariables.add(installationNode)
        }
        if (systemVariables.isNotEmpty()) {

            val systemNode = Node(name = ctxSystem.context(), children = systemVariables)
            node?.append(systemNode)
        }

        val systemHome = Variable.get(pathSystemHome)

        val utilsPath = FilePathBuilder()
            .addContext(systemHome)
            .addContext(Commands.DIRECTORY_CORE)
            .addContext(Commands.DIRECTORY_UTILS)
            .build()

        val utilsHome = Key.UtilsHome
        val systemUtilsNode = Node(name = utilsHome.key(), value = utilsPath)
        systemVariables.add(systemUtilsNode)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun initializeDockerVariables(config: Configuration) {

        var node: Node? = null
        config.variables?.let {
            node = it
        }
        if (node == null) {

            node = Node()
            config.variables = node
        }

        val ctxDocker = Context.Docker
        val keyDockerEnvironment = Key.DockerEnvironment

        val dockerVariables = mutableListOf<Node>()
        val factory = DefaultEnvironmentVariablesFactory()
        val environmentVariables = factory.obtain()
        val dockerEnvironment = Node(name = keyDockerEnvironment.key(), value = environmentVariables)
        dockerVariables.add(dockerEnvironment)

        val dockerNode = Node(name = ctxDocker.context(), children = dockerVariables)
        node?.append(dockerNode)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun initializeProxyVariables(config: Configuration, callback: Runnable) {

        var node: Node? = null
        config.variables?.let {
            node = it
        }
        if (node == null) {

            node = Node()
            config.variables = node
        }

        val ctxProxy = Context.Proxy

        if (config.proxy == null) {

            callback.run()
            return
        }

        config.proxy?.let { proxy ->

            @Throws(IllegalStateException::class, )
            fun initProxyVariables() {

                val keyHost = Key.Host
                val keyPort = Key.Port
                val keyAccount = Key.Account
                val keyHostName = Key.Hostname
                val keyPassword = Key.Password
                val keySelfSigned = Key.SelfSigned
                val keyCaEndpoint = Key.CaEndpoint
                val keyRefreshFrequency = Key.RefreshFrequency

                val proxyAccount = if (proxy.getProxyAccount().isEmpty()) {

                    Variable.EMPTY_VARIABLE
                } else {
                    proxy.getProxyAccount()
                }

                val proxyPassword = if (proxy.getProxyPassword().isEmpty()) {

                    Variable.EMPTY_VARIABLE
                } else {
                    proxy.getProxyPassword()
                }

                val proxyCertificateEndpoint = if (proxy.getCertificateEndpoint().isEmpty()) {

                    Variable.EMPTY_VARIABLE
                } else {
                    proxy.getCertificateEndpoint()
                }

                val proxyVariables = mutableListOf<Node>()

                val proxyPort = Node(name = keyPort.key(), value = proxy.port)
                val proxyHost = Node(name = keyHost.key(), value = proxy.getHost())
                val proxyHostName = Node(name = keyHostName.key(), value = proxy.getProxyHostname())
                val proxyAccountNode = Node(name = keyAccount.key(), value = proxyAccount)
                val proxyPasswordNode = Node(name = keyPassword.key(), value = proxyPassword)
                val proxySelfSigned = Node(name = keySelfSigned.key(), value = proxy.isSelfSignedCA())
                val proxyCaEndpoint = Node(name = keyCaEndpoint.key(), value = proxyCertificateEndpoint)
                val proxyRefreshFrequency = Node(name = keyRefreshFrequency.key(), value = proxy.getRefreshFrequency())

                proxyVariables.add(proxyPort)
                proxyVariables.add(proxyHost)
                proxyVariables.add(proxyHostName)
                proxyVariables.add(proxyAccountNode)
                proxyVariables.add(proxyPasswordNode)
                proxyVariables.add(proxySelfSigned)
                proxyVariables.add(proxyCaEndpoint)
                proxyVariables.add(proxyRefreshFrequency)

                val proxyNode = Node(name = ctxProxy.context(), children = proxyVariables)
                node?.append(proxyNode)

                callback.run()
            }

            val host = proxy.getHost(preferIpAddress = false)
            val ip4Validator = IPV4Validator()

            val behaviorPath = PathBuilder()
                .addContext(Context.Behavior)
                .setKey(Key.GetIp)
                .build()

            var behaviorGetIp = false
            val msg = "Get IP behavior setting"
            try {

                behaviorGetIp = Variable.get(behaviorPath).toBoolean()
                log.v("$msg (1): $behaviorGetIp")
            } catch (e: IllegalStateException) {

                log.v("$msg (2): $behaviorGetIp")
            }

            if (ip4Validator.validate(host)) {

                initProxyVariables()
            } else {

                if (behaviorGetIp) {

                    val connection = getConnection()
                    val cmd = IpAddressObtainCommand(host)
                    val handler = object : HostIpAddressDataHandler(proxy) {

                        @Throws(IllegalStateException::class, IllegalArgumentException::class)
                        override fun onData(data: OperationResult?) {
                            super.onData(data)

                            initProxyVariables()
                        }
                    }

                    CommandFlow()
                        .width(connection.getTerminal())
                        .perform(cmd, handler)
                        .run()
                } else {

                    initProxyVariables()
                }
            }
        }
    }

    private fun checkAndGetVariable(path: Path): String {

        var value = String.EMPTY
        try {

            value = Variable.get(path)
            log.v("Variable '${path.getPath()}' is defined")
        } catch (e: IllegalStateException) {

            log.v("Variable '${path.getPath()}' is not yet defined")
        }
        return value
    }

    private fun getHomeDirectory(): File {

        val home = System.getProperty("user.home")
        val homePath = FilePathBuilder().addContext(home).getPath()
        var systemHome = File("")
        if (systemHome.absolutePath == homePath) {
            systemHome = File(installationLocation)
        }
        return systemHome
    }

    private fun notifyInit() {

        free()
        val result = OperationResult(initializationOperation, true)
        notify(result)
    }
}