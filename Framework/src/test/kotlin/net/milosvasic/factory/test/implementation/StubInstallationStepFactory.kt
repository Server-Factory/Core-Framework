package net.milosvasic.factory.test.implementation

import net.milosvasic.factory.component.installer.step.InstallationStep
import net.milosvasic.factory.component.installer.step.deploy.Deploy
import net.milosvasic.factory.component.installer.step.deploy.DeployValidator
import net.milosvasic.factory.component.installer.step.factory.InstallationStepFactory
import net.milosvasic.factory.configuration.InstallationStepDefinition
import net.milosvasic.factory.configuration.definition.Definition
import net.milosvasic.factory.validation.Validator

class StubInstallationStepFactory(private val protoStubs: List<String>) : InstallationStepFactory {

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun obtain(vararg param: InstallationStepDefinition): InstallationStep<*> {

        Validator.Arguments.validateSingle(param)
        val definition = param[0]
        when (definition.type) {
            "stubDeploy" -> {

                val validator = DeployValidator()
                if (validator.validate(definition.getValue())) {

                    val fromTo = definition.getValue().split(Deploy.SEPARATOR_FROM_TO)
                    var from = fromTo[0].trim()
                    if (from == "${Definition.CURRENT_DEFINITION}${Deploy.SEPARATOR_DEFINITION}") {

                        from = definition.getDefinition().getHome().absolutePath
                    } else if (from.contains(Deploy.SEPARATOR_DEFINITION)) {

                        throw IllegalArgumentException("Unsupported stub deploy variation: $from")
                    }
                    val to = fromTo[1].trim()
                    return StubDeploy(from, to, protoStubs)
                } else {

                    throw IllegalArgumentException("Invalid stub deploy parameters")
                }
            }
            "stubStack" -> {

                return StubStack(definition.getValue())
            }
        }
        throw IllegalArgumentException("Unknown installation step type: ${definition.type}")
    }
}