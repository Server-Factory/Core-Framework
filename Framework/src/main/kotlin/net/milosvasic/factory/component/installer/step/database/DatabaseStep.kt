package net.milosvasic.factory.component.installer.step.database

import com.google.gson.Gson
import com.google.gson.JsonParseException
import net.milosvasic.factory.common.DataHandler
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.common.obtain.Obtain
import net.milosvasic.factory.component.database.*
import net.milosvasic.factory.component.database.manager.DatabaseManager
import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.execution.flow.implementation.InstallationStepFlow
import net.milosvasic.factory.execution.flow.implementation.ObtainableFlow
import net.milosvasic.factory.execution.flow.implementation.RegistrationFlow
import net.milosvasic.factory.log
import net.milosvasic.factory.operation.OperationResult
import net.milosvasic.factory.operation.OperationResultListener
import net.milosvasic.factory.remote.Connection
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.terminal.command.CatCommand
import net.milosvasic.factory.terminal.command.TestCommand
import java.nio.file.InvalidPathException

class DatabaseStep(val path: String) : RemoteOperationInstallationStep<SSH>() {

    companion object {

        const val DEFAULT_CONFIGURATION_FILE = "configuration.json"
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->

            val manager = DatabaseManager.instantiate() ?: throw IllegalStateException("Invalid database manager")
            var config: DatabaseConfiguration? = null

            val configurationFile = FilePathBuilder()
                    .addContext(path)
                    .addContext(DEFAULT_CONFIGURATION_FILE)
                    .build()

            val configurationDataHandler = object : DataHandler<OperationResult> {
                override fun onData(data: OperationResult?) {

                    data?.let {
                        val json = it.data
                        val gson = Gson()
                        try {

                            config = gson.fromJson(json, DatabaseConfiguration::class.java)
                            log.i("Database configuration available: $config")
                        } catch (e: JsonParseException) {

                            log.e(e)
                        } catch (e: IllegalArgumentException) {

                            log.e(e)
                        }
                    }
                }
            }

            val databaseRegistrationProvider = object : Obtain<DatabaseRegistration> {
                override fun obtain(): DatabaseRegistration {
                    return getDatabaseRegistration(conn, config)
                }
            }

            val registrationFlow = RegistrationFlow<DatabaseRegistration>("Database Registration")
                    .width(manager)
                    .perform(databaseRegistrationProvider)

            val databaseFlow = ObtainableFlow("Database").width(
                    object : Obtain<InstallationStepFlow> {

                        @Throws(InvalidPathException::class)
                        override fun obtain(): InstallationStepFlow {

                            val db = databaseRegistrationProvider.obtain().database
                            val installation = db.getInstallation()
                            config?.let { conf ->
                                if (conf.sqls.isNotEmpty()) {
                                    val sqlFlow = CommandFlow("Sql").width(conn)
                                    conf.sqls.forEach { sql ->

                                        val sqlPath = FilePathBuilder()
                                                .addContext(path)
                                                .addContext(sql)
                                                .build()

                                        log.v("SQL: $sqlPath")
                                        val sqlCommand = db.getSqlCommand(sqlPath)
                                        sqlFlow.perform(sqlCommand)
                                    }
                                    installation.connect(sqlFlow)
                                }
                            }
                            return installation
                        }
                    }
            )

            return CommandFlow("Database")
                    .width(conn)
                    .perform(TestCommand(configurationFile))
                    .perform(CatCommand(configurationFile), configurationDataHandler)
                    .connect(registrationFlow)
                    .connect(databaseFlow)
        }
        throw IllegalArgumentException("No proper connection provided")
    }

    override fun getOperation() = DatabaseInitializationOperation()

    @Throws(IllegalArgumentException::class)
    private fun getDatabaseRegistration(
            connection: Connection,
            configuration: DatabaseConfiguration?

    ): DatabaseRegistration {
        configuration?.let {

            val type = Type.getType(it.type)
            val dbConnection = DatabaseConnection(
                    it.host,
                    it.getPort(),
                    it.user,
                    it.password,
                    connection
            )
            val factory = DatabaseFactory(type, it.name, dbConnection)
            val database = factory.build()

            val callback = object : OperationResultListener {
                override fun onOperationPerformed(result: OperationResult) {
                    when (result.operation) {
                        is DatabaseRegistrationOperation -> {
                            if (!result.success) {
                                log.e("Database registration failed: $database")
                            }
                        }
                    }
                }
            }

            return DatabaseRegistration(database, callback)
        }
        throw IllegalArgumentException("Proper database configuration unavailable")
    }
}