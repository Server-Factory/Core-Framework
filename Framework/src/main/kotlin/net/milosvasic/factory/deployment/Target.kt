package net.milosvasic.factory.deployment

data class Target(

    val name: String,
    private val type: String,
    val source: String
) {

    fun getType(): TargetType {

        return TargetType.getByValue(type)
    }
}