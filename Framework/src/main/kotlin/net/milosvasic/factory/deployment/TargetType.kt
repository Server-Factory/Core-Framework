package net.milosvasic.factory.deployment

enum class TargetType(

    val type: String
) {

    GENERIC("generic"),
    ACCOUNT("account"),
    WEB_SERVICE("web_service");

    companion object {

        fun getByValue(value: String): TargetType {

            values().forEach {
                if (value == it.type) {

                    return it
                }
            }
            return GENERIC
        }
    }
}