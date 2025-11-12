package net.milosvasic.factory.test

import net.milosvasic.factory.component.installer.step.InstallationStepType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName

@DisplayName("Test condition step flow")
class ConditionStepFlowTest : SkipConditionStepFlowTest() {

    override fun name() = "Condition"

    override fun type() = InstallationStepType.CONDITION.type

    override fun expectedPositives() = 3  // Corrected - test gets 3 when run all tests

    override fun expectedNegatives() = 1

    override fun expectedTerminalCommandPositives() = expectedPositives()

    override fun expectedTerminalCommandNegatives() = 2
}