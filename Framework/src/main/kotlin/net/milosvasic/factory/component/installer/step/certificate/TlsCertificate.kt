package net.milosvasic.factory.component.installer.step.certificate

import net.milosvasic.factory.common.filesystem.FilePathBuilder
import net.milosvasic.factory.component.Toolkit
import net.milosvasic.factory.component.installer.recipe.CommandInstallationStepRecipe
import net.milosvasic.factory.component.installer.recipe.ConditionRecipe
import net.milosvasic.factory.component.installer.step.CommandInstallationStep
import net.milosvasic.factory.component.installer.step.condition.SkipCondition
import net.milosvasic.factory.configuration.variable.Context
import net.milosvasic.factory.configuration.variable.Key
import net.milosvasic.factory.configuration.variable.PathBuilder
import net.milosvasic.factory.configuration.variable.Variable
import net.milosvasic.factory.execution.flow.implementation.CommandFlow
import net.milosvasic.factory.execution.flow.implementation.InstallationStepFlow
import net.milosvasic.factory.security.Permission
import net.milosvasic.factory.security.Permissions
import net.milosvasic.factory.terminal.command.Commands
import net.milosvasic.factory.terminal.command.ConcatenateCommand
import net.milosvasic.factory.terminal.command.TestCommand

class TlsCertificate(name: String) : Certificate(name) {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun getFlow(): CommandFlow {

        connection?.let { conn ->

            val subject = Commands.getOpensslSubject()
            val hostname = conn.getRemoteOS().getHostname()

            val path = PathBuilder()
                    .addContext(Context.Server)
                    .addContext(Context.Certification)
                    .setKey(Key.Certificates)
                    .build()

            val pathCaBundle = PathBuilder()
                    .addContext(Context.Server)
                    .addContext(Context.Certification)
                    .setKey(Key.CaBundle)
                    .build()

            val pathPassPhrase = PathBuilder()
                    .addContext(Context.Server)
                    .addContext(Context.Certification)
                    .setKey(Key.Passphrase)
                    .build()

            val certificatesPath = Variable.get(path)
            val caBundle = Variable.get(pathCaBundle)
            val passPhrase = Variable.get(pathPassPhrase)

            val passIn = "-passin pass:$passPhrase"
            val passOut = "-passout pass:$passPhrase"
            val permission600 = Permissions(Permission(6), Permission.NONE, Permission.NONE).obtain()

            val crtVerificationCommand = TestCommand(

                    FilePathBuilder()
                            .addContext(certificatesPath)
                            .addContext("$hostname.crt")
                            .build()
            )

            val keyVerificationCommand = TestCommand(

                    FilePathBuilder()
                            .addContext(certificatesPath)
                            .addContext("$hostname.key")
                            .build()
            )

            val caVerificationCommand = TestCommand(

                    FilePathBuilder()
                            .addContext(certificatesPath)
                            .addContext(caBundle)
                            .build()
            )

            val installation = ConcatenateCommand(
                    Commands.cd(certificatesPath),
                    Commands.openssl("genrsa $passOut -aes128 2048 > $hostname.key"),
                    Commands.openssl("rsa $passIn -in $hostname.key -out $hostname.key"),
                    Commands.openssl("req -subj $subject -utf8 -new -key $hostname.key -out $hostname.csr"),
                    Commands.openssl("x509 -in $hostname.csr -out $hostname.crt -req -signkey $hostname.key -days 3650"),
                    Commands.chmod("$hostname.key", permission600)
            )

            val toolkit = Toolkit(conn)
            val installationFlow = InstallationStepFlow(toolkit, "Tls Certificate Installation")
                    .registerRecipe(SkipCondition::class, ConditionRecipe::class)
                    .registerRecipe(CommandInstallationStep::class, CommandInstallationStepRecipe::class)
                    .width(CommandInstallationStep(caVerificationCommand))
                    .width(SkipCondition(crtVerificationCommand))
                    .width(SkipCondition(keyVerificationCommand))
                    .width(CommandInstallationStep(installation))

            val completionFlow = CommandFlow("Tls Certificate Completion")
                    .width(conn)
                    .perform(caVerificationCommand)
                    .perform(keyVerificationCommand)
                    .perform(crtVerificationCommand)

            return CommandFlow("Tls Certificate")
                    .width(conn)
                    .perform(TestCommand(certificatesPath))
                    .connect(installationFlow)
                    .connect(completionFlow)
        }
        throw IllegalArgumentException("No proper connection provided")
    }
}