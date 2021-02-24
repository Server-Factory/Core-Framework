package net.milosvasic.factory.component.docker.environment

import net.milosvasic.factory.common.obtain.Obtain
import java.lang.StringBuilder

class DefaultEnvironmentVariablesFactory : Obtain<String> {

    override fun obtain() = StringBuilder()
        .append("${EnvironmentVariables.FactoryService.variableName}=true")
        .toString()
}