package net.blueshell.api.factory.model.contribution

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import org.junit.jupiter.api.Test

class ContributionFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable contribution`() {
        val period = persistContributionPeriod()
        val user = persistUser()

        val contribution = contributionFactory.createBasic()
        contribution.user = user
        contribution.contributionPeriod = period

        val saved = persist(contribution)
        assertPersisted(_root_ide_package_.net.blueshell.api.domain.contribution.persistence.Contribution::class.java, saved.id)
    }
}
