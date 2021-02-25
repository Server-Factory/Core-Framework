package net.milosvasic.factory.component.docker.environment

import net.milosvasic.factory.common.obtain.Obtain
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import java.lang.StringBuilder
import kotlin.jvm.Throws

class DefaultEnvironmentVariablesFactory : Obtain<String> {

    @Throws(IllegalStateException::class)
    override fun obtain(): String {

        val utilsPath = PathBuilder()
            .addContext(Context.System)
            .setKey(Key.UtilsHome)
            .build()

        val utilsHome = Variable.get(utilsPath)

        return StringBuilder()
            .append("${EnvironmentVariables.FactoryService.variableName}=true")
            .append("${EnvironmentVariables.UtilsHome.variableName}=$utilsHome")
            .toString()
    }
}