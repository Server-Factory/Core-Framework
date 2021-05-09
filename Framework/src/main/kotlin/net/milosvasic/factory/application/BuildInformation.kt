package net.milosvasic.factory.application

interface BuildInformation {

    val productName: String
    val versionName: String
    val version: String
    val versionCode: Int

    fun printName(): String
}