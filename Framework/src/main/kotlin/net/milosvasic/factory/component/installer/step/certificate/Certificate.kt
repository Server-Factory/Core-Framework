package net.milosvasic.factory.component.installer.step.certificate

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.component.Toolkit
import net.milosvasic.factory.component.installer.recipe.CommandInstallationStepRecipe
import net.milosvasic.factory.component.installer.recipe.ConditionRecipe
import net.milosvasic.factory.component.installer.step.CommandInstallationStep
import net.milosvasic.factory.component.installer.step.RemoteOperationInstallationStep
import net.milosvasic.factory.component.installer.step.condition.SkipCondition
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.execution.flow.implementation.InstallationStepFlow
import net.milosvasic.factory.remote.ssh.SSH
import net.milosvasic.factory.security.Permission
import net.milosvasic.factory.security.Permissions
import net.milosvasic.factory.terminal.command.*
import java.io.File

open class Certificate(val name: String) : RemoteOperationInstallationStep<SSH>() {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->

            val hostname = conn.getRemoteOS().getHostname()

            val keyPath = PathBuilder()
                    .addContext(Context.Server)
                    .addContext(Context.Certification)
                    .setKey(Key.Certificates)
                    .build()

            val homeKeyPath = PathBuilder()
                    .addContext(Context.Server)
                    .addContext(Context.Certification)
                    .setKey(Key.Home)
                    .build()

            val path = Variable.get(keyPath)
            val certHome = Variable.get(homeKeyPath)
            val certificates = Variable.get(keyPath)

            val permission = Permissions(Permission(6), Permission.NONE, Permission.NONE)
            val perm = permission.obtain()

            val certificateExtension = ".crt"
            val issued = FilePathBuilder()
                    .addContext(File.separator)
                    .addContext("pki")
                    .addContext("issued")
                    .addContext(File.separator)
                    .build()

            val linkingPath = FilePathBuilder()
                    .addContext(certificates)
                    .addContext(hostname)
                    .addContext(certificateExtension)
                    .build()

            val verificationPath = "$certHome$issued$hostname$certificateExtension"
            val verificationCommand = TestCommand(verificationPath)

            val genPrivate = GeneratePrivateKeyCommand(path, name)
            val genRequest = GenerateRequestKeyCommand(path, Commands.getPrivateKyName(name), name)
            val impRequest = ImportRequestKeyCommand(path, Commands.getRequestKeyName(name), hostname)
            val sign = SignRequestKeyCommand(hostname)
            val chmod = ChmodCommand(path, perm)
            val copy = CpCommand(verificationPath, linkingPath)
            val pem = GeneratePEMCommand()

            val toolkit = Toolkit(conn)
            val checkFlow = InstallationStepFlow(toolkit, "Certificate Installation")
                    .registerRecipe(SkipCondition::class, ConditionRecipe::class)
                    .registerRecipe(CommandInstallationStep::class, CommandInstallationStepRecipe::class)
                    .width(SkipCondition(verificationCommand))
                    .width(CommandInstallationStep(genPrivate))
                    .width(CommandInstallationStep(genRequest))
                    .width(CommandInstallationStep(impRequest))
                    .width(CommandInstallationStep(sign))
                    .width(CommandInstallationStep(copy))
                    .width(CommandInstallationStep(pem))
                    .width(CommandInstallationStep(chmod))

            val completionFlow = CommandFlow("Certificate Completion")
                    .width(conn)
                    .perform(verificationCommand)
                    .perform(TestCommand(linkingPath))

            return CommandFlow("Certificate")
                    .width(conn)
                    .perform(MkdirCommand(path))
                    .connect(checkFlow)
                    .connect(completionFlow)
        }
        throw IllegalArgumentException("No proper connection provided")
    }

    override fun getOperation() = CertificateInitializationOperation()
}