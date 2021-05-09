package net.milosvasic.factory.behavior

import net.milosvasic.factory.configuration.variable.*

class Behavior {

    fun behaviorGetIp(): Boolean {

        val behavior = PathBuilder()
            .addContext(Context.Behavior)
            .setKey(Key.GetIp)
            .build()

        return getBehavior(behavior)
    }

    fun behaviorDisableIptablesForMdns(): Boolean {

        val path = PathBuilder()
            .addContext(Context.Behavior)
            .setKey(Key.DisableIptablesForMdns)
            .build()

        return getBehavior(path)
    }

    private fun getBehavior(behaviorPath: Path): Boolean {

        var behavior = false
        try {

            behavior = Variable.get(behaviorPath).toBoolean()
        } catch (e: IllegalStateException) {

            // Ignore
        }
        return behavior
    }
}