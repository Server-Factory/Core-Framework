package net.milosvasic.factory.component.docker

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.common.obtain.Obtain
import net.milosvasic.factory.configuration.variable.*
import java.io.File

enum class DockerCommand : Obtain<String> {

    DOCKER {
        override fun obtain() = "docker"
    },
    RUN {
        override fun obtain() = "run"
    },
    PS {
        override fun obtain() = "ps"
    },
    BUILD {
        override fun obtain() = "build"
    },
    UP {
        override fun obtain() = "up"
    },
    DOWN {
        override fun obtain() = "down"
    },
    COMMIT {
        override fun obtain() = "commit"
    },
    NETWORK {
        override fun obtain() = "network"
    },
    CREATE {
        override fun obtain() = "create"
    },
    NETWORK_CREATE {

        override fun obtain() = "${DOCKER.obtain()} ${NETWORK.obtain()} ${CREATE.obtain()}"
    },
    COMPOSE {

        @Throws(IllegalStateException::class)
        override fun obtain(): String {

            val path = PathBuilder()
                    .addContext(Context.Docker)
                    .setKey(Key.DockerComposePath)
                    .build()

            val variable = Variable.get(path)
            return if (variable != String.EMPTY) {
                "$variable${File.separator}docker-compose"
            } else {
                "docker-compose"
            }
        }
    },
    START {
        override fun obtain() = "start"
    },
    STOP {
        override fun obtain() = "stop"
    },
    KILL {
        override fun obtain() = "kill"
    },
    VERSION {
        override fun obtain() = "--version"
    }
}