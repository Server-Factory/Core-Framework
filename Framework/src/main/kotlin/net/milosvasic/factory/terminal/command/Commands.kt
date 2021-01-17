package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.EMPTY
import net.milosvasic.factory.FILE_LOCATION_HERE
import net.milosvasic.factory.LOCALHOST
import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.proxy.Proxy
import net.milosvasic.factory.remote.Remote
import java.nio.file.InvalidPathException

object Commands {

    const val RM = "rm"
    const val CP = "cp -R"
    const val SSH = "ssh -p"
    const val SCP = "scp -P"
    const val UNAME = "uname"
    const val HOSTNAME = "hostname"
    const val HERE = FILE_LOCATION_HERE
    const val TAR_EXTENSION = ".tar.gz"

    private const val CD = "cd"
    private const val BASH = "sh"
    private const val FIND = "find "
    private const val LINK = "ln -s"
    private const val SLEEP = "sleep"
    private const val MKDIR = "mkdir -p"
    private const val CHMOD = "chmod -R"
    private const val CHGRP = "chgrp -R"
    private const val CHOWN = "chown -R"
    private const val OPENSSL = "openssl"
    private const val TAR_COMPRESS = "tar -cjf"
    private const val TAR_DECOMPRESS = "tar -xvf"

    const val DIRECTORY_CORE = "Core"
    const val DIRECTORY_UTILS = "Utils"

    private const val SCRIPT_GET_IP = "getip.sh"
    private const val SCRIPT_SET_HOSTNAME = "set_hostname.sh"
    private const val SCRIPT_INSTALL_PROXY = "proxy_install.sh"

    fun echo(what: String) = "echo '$what'"

    fun sleep(duration: Int) = "$SLEEP $duration"

    fun printf(what: String) = "printf '$what'"

    fun ssh(user: String = "root", command: String, port: Int = 22, host: String = LOCALHOST): String {
        return "$SSH $port $user@$host $command"
    }

    fun ping(host: String, timeoutInSeconds: Int = 3): String {
        return "ping $host -c $timeoutInSeconds"
    }

    fun getHostInfo(): String = "hostnamectl"

    @Throws(InvalidPathException::class)
    fun getIpAddress(host: String): String {

        val path = PathBuilder()
            .addContext(Context.System)
            .setKey(Key.Home)
            .build()

        val systemHome = Variable.get(path)

        val filePath = FilePathBuilder()
            .addContext(systemHome)
            .addContext(DIRECTORY_CORE)
            .addContext(DIRECTORY_UTILS)
            .addContext(SCRIPT_GET_IP)
            .build()

        return "$BASH $filePath $host"
    }

    @Throws(InvalidPathException::class, IllegalStateException::class)
    fun installProxy(proxy: Proxy): String {

        val rootPath = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.ServerHome)
            .build()

        val root = Variable.get(rootPath)

        val path = FilePathBuilder()
            .addContext(root)
            .addContext(DIRECTORY_UTILS)
            .addContext(SCRIPT_INSTALL_PROXY)
            .build()

        val selfSignedCa = if (proxy.isSelfSignedCA()) {

            "true"
        } else {
            String.EMPTY
        }
        return "$BASH $path ${proxy.getHost()} ${proxy.port} " +
                "${proxy.getProxyAccount()} ${proxy.getProxyPassword()} $selfSignedCa"
    }

    fun getApplicationInfo(application: String): String = "which $application"

    fun reboot(rebootIn: Int = 2) = "( $SLEEP $rebootIn ; reboot ) & "

    @Throws(IllegalStateException::class)
    fun scp(what: String, where: String, remote: Remote): String {

        return "$SCP ${remote.port} $what ${remote.getAccountName()}@${remote.getHost()}:$where"
    }

    fun cp(what: String, where: String): String {

        return "$CP $what $where"
    }

    fun find(what: String, where: String) = "$FIND$where -name \"$what\""

    fun tar(what: String, where: String): String {

        val destination = where.replace(".tar", "").replace(".gz", "")
        return "$TAR_COMPRESS $destination$TAR_EXTENSION -C $what ."
    }

    fun unTar(what: String, where: String) = "$TAR_DECOMPRESS $what -C $where"

    fun rm(what: String) = "$RM $what"

    fun chmod(where: String, mode: String) = "$CHMOD $mode $where"

    fun chgrp(group: String, directory: String) = "$CHGRP $group $directory"

    fun chown(account: String, directory: String) = "$CHOWN $account $directory"

    fun mkdir(path: String) = "$MKDIR $path"

    fun concatenate(vararg commands: String): String {

        var result = String.EMPTY
        commands.forEach {
            if (result.isNotEmpty() && result.isNotBlank()) {
                result += "; "
            }
            result += it
        }
        return result
    }

    fun test(what: String) = "test -e $what"

    fun setHostName(hostname: String): String {

        val local = ".local"
        val toSet = if (hostname.endsWith(local)) {

            hostname.replace(local, "")
        } else {
            hostname
        }

        val path = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.ServerHome)
            .build()

        val serverHome = Variable.get(path)

        val filePath = FilePathBuilder()
            .addContext(serverHome)
            .addContext(DIRECTORY_UTILS)
            .addContext(SCRIPT_SET_HOSTNAME)
            .build()

        return "$BASH $filePath $toSet"
    }

    fun cat(what: String) = "cat $what"

    fun openssl(command: String) = Variable.parse("$OPENSSL $command")

    @Throws(InvalidPathException::class)
    fun generatePrivateKey(path: String, name: String): String {

        val keyName = getPrivateKyName(name)
        val param = FilePathBuilder()
            .addContext(path)
            .addContext(keyName)
            .build()

        return "$OPENSSL genrsa -out $param"
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun generateRequestKey(path: String, keyName: String, reqName: String): String {

        val params = getOpensslSubject()
        val requestKey = getRequestKeyName(reqName)
        val cmd = "$OPENSSL req -new -key"

        val reqKey = FilePathBuilder()
            .addContext(path)
            .addContext(requestKey)
            .build()

        val verify = "openssl req -in $reqKey -noout -subject"
        val param = FilePathBuilder()
            .addContext(path)
            .addContext(keyName)
            .build()

        return "$cmd $param -out $reqKey -subj $params && $verify"
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun importRequestKey(path: String, requestKey: String, name: String): String {

        val homePath = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Home)
            .build()

        val home = Variable.get(homePath)
        val key = FilePathBuilder()
            .addContext(path)
            .addContext(requestKey)
            .build()

        return "cd $home && ./easyrsa import-req $key $name"
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun signRequestKey(name: String): String {

        val path = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Home)
            .build()

        val passPhrasePath = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Passphrase)
            .build()

        val home = Variable.get(path)
        val passPhrase = Variable.get(passPhrasePath)

        val passIn = "export EASYRSA_PASSIN='pass:$passPhrase'"
        val passOut = "export EASYRSA_PASSOUT='pass:$passPhrase'"
        val passwords = "$passIn && $passOut"
        return "cd $home && $passwords && echo 'yes' | ./easyrsa sign-req server $name"
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun generatePEM(keyName: String = "cakey.pem", certName: String = "cacert.pem"): String {

        val subject = getOpensslSubject()
        val req = "req -subj $subject -new -x509 -extensions v3_ca -keyout $keyName -out $certName -days 3650"

        val path = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Certificates)
            .build()

        val passPhrasePath = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Passphrase)
            .build()

        val home = Variable.get(path)
        val passPhrase = Variable.get(passPhrasePath)

        val passIn = "-passin pass:$passPhrase"
        val passOut = "-passout pass:$passPhrase"
        val password = "$passIn $passOut"

        return "cd $home && $OPENSSL $req $password"
    }

    fun getPrivateKyName(name: String): String {
        var fullName = name
        val extension = ".key"
        val prefix = "private."
        if (!fullName.endsWith(extension)) {
            fullName += extension
        }
        if (!fullName.startsWith(prefix)) {
            fullName = prefix + fullName
        }
        return fullName
    }

    fun getRequestKeyName(reqName: String): String {
        var fullName = reqName
        val extension = ".req"
        val prefix = "request."
        if (!fullName.endsWith(extension)) {
            fullName += extension
        }
        if (!fullName.startsWith(prefix)) {
            fullName = prefix + fullName
        }
        return fullName
    }

    fun cd(where: String) = "$CD $where"

    fun link(what: String, where: String) = "$LINK $what $where"

    fun portAvailable(port: Int) = "! lsof -i:$port | grep LISTEN"

    fun portTaken(port: Int, timeout: Int): String {

        val serverHomePath = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.ServerHome)
            .build()

        val serverHome = Variable.get(serverHomePath)

        val command = FilePathBuilder()
            .addContext(serverHome)
            .addContext("Utils")
            .addContext("checkPortBound.sh")
            .build()

        return "$command $port $timeout"
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun getOpensslSubject(): String {

        val pathHostname = PathBuilder()
            .addContext(Context.Server)
            .setKey(Key.Hostname)
            .build()

        val pathCity = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.City)
            .build()

        val pathCountry = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Country)
            .build()

        val pathProvince = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Province)
            .build()

        val pathDepartment = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Department)
            .build()

        val pathOrganisation = PathBuilder()
            .addContext(Context.Server)
            .addContext(Context.Certification)
            .setKey(Key.Organisation)
            .build()

        val hostname = Variable.get(pathHostname)
        val city = Variable.get(pathCity)
        val country = Variable.get(pathCountry)
        val province = Variable.get(pathProvince)
        val department = Variable.get(pathDepartment)
        val organisation = Variable.get(pathOrganisation)
        var subject = "/C=$country/ST=$province/L=$city/O=$organisation/OU=$department/CN=$hostname"
        subject = subject.replace(" ", "\\ ")
        return subject
    }
}