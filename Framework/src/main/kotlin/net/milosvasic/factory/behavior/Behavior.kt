package net.milosvasic.factory.behavior

import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable

class Behavior {

    fun behaviorGetIp(): Boolean {

        val behaviorPath = PathBuilder()
            .addContext(Context.Behavior)
            .setKey(Key.GetIp)
            .build()

        var behaviorGetIp = false
        try {

            behaviorGetIp = Variable.get(behaviorPath).toBoolean()
        } catch (e: IllegalStateException) {

            // Ignore
        }
        return behaviorGetIp
    }
}