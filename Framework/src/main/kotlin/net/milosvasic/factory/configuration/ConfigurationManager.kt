package net.milosvasic.factory.configuration

import net.milosvasic.factory.DIRECTORY_DEFAULT_INSTALLATION_LOCATION
import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.busy.Busy
import net.milosvasic.factory.common.busy.BusyWorker
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.initialization.Initialization
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.configuration.definition.provider.DefinitionProvider
import net.milosvasic.factory.configuration.definition.provider.FilesystemDefinitionProvider
import net.milosvasic.factory.configuration.recipe.ConfigurationRecipe
import net.milosvasic.factory.configuration.recipe.FileConfigurationRecipe
import net.milosvasic.factory.configuration.recipe.RawJsonConfigurationRecipe
import net.milosvasic.factory.configuration.variable.*
import net.milosvasic.factory.log
import net.milosvasic.factory.os.OperatingSystem
import net.milosvasic.factory.validation.JsonValidator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object ConfigurationManager : Initialization {

    private val busy = Busy()
    private var loaded = AtomicBoolean()
    private val loading = AtomicBoolean()
    private var configuration: Configuration? = null
    private var recipe: ConfigurationRecipe<*>? = null
    private lateinit var definitionProvider: DefinitionProvider
    private var configurationFactory: ConfigurationFactory<*>? = null
    private var configurations = mutableListOf<SoftwareConfiguration>()
    private var installationLocation = DIRECTORY_DEFAULT_INSTALLATION_LOCATION

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun initialize() {

        checkInitialized()
        BusyWorker.busy(busy)
        if (configurationFactory == null) {

            throw IllegalStateException("Configuration factory was not provided")
        }
        if (recipe == null) {

            throw IllegalStateException("Configuration recipe was not provided")
        }
        recipe?.let { rcp ->

            configuration = configurationFactory?.obtain(rcp)
            nullConfigurationCheck()
            BusyWorker.free(busy)
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

            initializeSystemVariables(config)

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

    private fun initializeSystemVariables(config: Configuration) {

        var node: Node? = null
        config.variables?.let {
            node = it
        }
        if (node == null) {
            node = Node()
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
    }

    private fun checkAndGetVariable(path: Path): String {

        var value = ""
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
}