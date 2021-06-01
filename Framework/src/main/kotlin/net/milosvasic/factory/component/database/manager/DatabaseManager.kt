package net.milosvasic.factory.component.database.manager

import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.common.DataHandler
import net.milosvasic.factory.common.Registration
import net.milosvasic.factory.common.busy.BusyWorker
import net.milosvasic.factory.common.initialization.Initializer
import net.milosvasic.factory.common.initialization.Termination
import net.milosvasic.factory.common.obtain.Instantiate
import net.milosvasic.factory.common.obtain.ObtainParametrized
import net.milosvasic.factory.component.database.*
import net.milosvasic.factory.component.database.postgres.PostgresDatabasesListCommand
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.execution.flow.callback.FlowCallback
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.execution.flow.implementation.initialization.InitializationFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.operation.OperationResultListener
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.validation.Validator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

open class DatabaseManager(entryPoint: Connection) :
        BusyWorker<DatabaseManager>(entryPoint),
        ObtainParametrized<DatabaseRequest, Database>,
        Registration<DatabaseRegistration>,
        Initializer,
        Termination {

    private val initialized = AtomicBoolean()
    private var registration: DatabaseRegistration? = null
    private val operation = DatabaseRegistrationOperation()
    private val databases = ConcurrentHashMap<Type, MutableMap<String, Database>>()

    companion object : Instantiate<DatabaseManager?> {

        private var manager: DatabaseManager? = null

        @Throws(IllegalStateException::class)
        override fun instantiate(): DatabaseManager? {

            if (manager == null) {

                throw IllegalStateException("Database manager is not initialized")
            }
            return manager
        }
    }

    private val initFlowCallback = object : FlowCallback {
        override fun onFinish(success: Boolean) {

            registration?.let {

                val db = it.database
                val name = db.name
                val type = db.type

                if (success) {

                    var dbs = databases[type]
                    if (dbs == null) {
                        dbs = mutableMapOf()
                        databases[type] = dbs
                    }
                    dbs[name] = it.database
                    log.v("$type database initialized: '$name'")
                } else {

                    log.e("Database initialization failed for ${type.type} database")
                }
                val result = OperationResult(operation, success)
                it.callback.onOperationPerformed(result)
            }
            registration = null
            free()
        }
    }

    @Synchronized
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    override fun initialize() {

        checkInitialized()
        busy()
        if (manager == null) {
            manager = this
        }
        initialized.set(true)
        log.i("Database manager has been initialized")
        free()
        val operation = DatabaseManagerInitializationOperation()
        val operationResult = OperationResult(operation, true)
        notify(operationResult)
    }

    @Throws(IllegalStateException::class)
    open fun loadDatabasesFlow(): CommandFlow {

        val flow = CommandFlow("Load Database").width(entryPoint)
        Type.values().forEach { databaseType ->
            when (databaseType) {
                Type.Postgres -> {

                    val host = LOCALHOST

                    val portPath = PathBuilder()
                            .addContext(Context.Service)
                            .addContext(Context.Database)
                            .addContext(Context.Ports)
                            .setKey(Key.PortExposed)
                            .build()

                    val userPath = PathBuilder()
                            .addContext(Context.Service)
                            .addContext(Context.Database)
                            .setKey(Key.User)
                            .build()

                    val passPath = PathBuilder()
                            .addContext(Context.Service)
                            .addContext(Context.Database)
                            .setKey(Key.Password)
                            .build()

                    val port = Variable.get(portPath)
                    val user = Variable.get(userPath)
                    val password = Variable.get(passPath)

                    val command = PostgresDatabasesListCommand(
                            host,
                            port.toInt(),
                            user,
                            password
                    )

                    val handler = object : DataHandler<OperationResult> {

                        override fun onData(data: OperationResult?) {
                            data?.let {
                                it.data.split("\n").forEach { db ->

                                    try {
                                        val callback = object : OperationResultListener {
                                            override fun onOperationPerformed(result: OperationResult) {
                                                when (result.operation) {
                                                    is DatabaseRegistrationOperation -> {

                                                        if (!result.success) {
                                                            log.e("Database registration failed: $db")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        val dbConnection = DatabaseConnection(
                                                host,
                                                port.toInt(),
                                                user,
                                                password,
                                                entryPoint
                                        )
                                        val factory = DatabaseFactory(databaseType, db, dbConnection)
                                        val database = factory.build()
                                        val registration = DatabaseRegistration(database, callback)
                                        doRegister(registration)

                                    } catch (e: IllegalStateException) {

                                        log.e(e)
                                    }
                                }
                            }
                        }
                    }
                    flow.perform(command, handler)
                }
                else -> {

                    if (databaseType != Type.Unknown) {
                        log.v("Skipping '$databaseType' database type: not supported yet.")
                    }
                }
            }
        }
        return flow
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun register(what: DatabaseRegistration) {
        busy()
        doRegister(what)
    }

    @Throws(IllegalArgumentException::class)
    override fun unRegister(what: DatabaseRegistration) {

        val db = what.database
        val name = db.name
        val type = db.type

        if (databases[type]?.get(name) == what.database) {
            databases[type]?.remove(name)?.terminate()

            val result = OperationResult(operation, true)
            what.callback.onOperationPerformed(result)
        } else {

            log.e("Database instance is not registered: $databases")
            val result = OperationResult(operation, false)
            what.callback.onOperationPerformed(result)
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun obtain(vararg param: DatabaseRequest): Database {

        Validator.Arguments.validateSingle(param)
        val request = param[0]
        val type = request.type
        val name = request.name
        databases[type]?.get(name)?.let {
            return it
        }
        throw IllegalArgumentException("No database registered with the name '$name' for type: ${type.type}")
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun checkInitialized() {
        if (isInitialized()) {
            throw IllegalStateException("Database manager is already initialized")
        }
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun checkNotInitialized() {
        if (!isInitialized()) {
            throw IllegalStateException("Database manager has not been initialized")
        }
    }

    override fun isInitialized() = initialized.get()

    @Synchronized
    @Throws(IllegalStateException::class)
    override fun terminate() {
        busy()
        log.v("Shutting down: $this")
        val pairs = mutableListOf<Pair<Type, String>>()
        val iterator = databases.keys.iterator()
        while (iterator.hasNext()) {
            val type = iterator.next()
            val keyIterator = databases[type]?.keys?.iterator()
            keyIterator?.let {
                while (it.hasNext()) {
                    val name = it.next()
                    val pair = Pair(type, name)
                    pairs.add(pair)
                }
            }
        }
        pairs.forEach { pair ->
            unRegister(pair.first, pair.second)
        }
        manager = null
        free()
    }

    private fun unRegister(type: Type, name: String) {
        databases[type]?.remove(name)?.terminate()
    }

    override fun notify(success: Boolean) {
        val operation = DatabaseManagerInitializationOperation()
        val result = OperationResult(operation, true)
        notify(result)
    }

    override fun onSuccessResult() {
        free(true)
    }

    override fun onFailedResult() {
        free(false)
    }

    private fun doRegister(what: DatabaseRegistration) {

        registration = what
        val db = what.database
        InitializationFlow("Database Registration")
                .width(db)
                .onFinish(initFlowCallback)
                .run()
    }
}