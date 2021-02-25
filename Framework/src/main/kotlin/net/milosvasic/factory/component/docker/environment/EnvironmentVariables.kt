package net.milosvasic.factory.component.docker.environment

enum class EnvironmentVariables(val variableName: String) {

    FactoryService("factory_service"),
    UtilsHome("utils_home")
}