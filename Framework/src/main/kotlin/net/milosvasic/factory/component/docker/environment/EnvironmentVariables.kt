package net.milosvasic.factory.component.docker.environment

enum class EnvironmentVariables(val variableName: String) {

    FactoryService("factory_service"),
    HttpProxy("http_proxy"),
    HttpsProxy("https_proxy"),
    FtpProxy("ftp_proxy"),
    NoProxy("no_proxy")
}