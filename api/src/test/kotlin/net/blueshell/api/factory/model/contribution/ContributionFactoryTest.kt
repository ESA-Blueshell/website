package net.blueshell.api.factory.model.contribution

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.contribution.domain.model.Contribution
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
        assertPersisted(Contribution::class.java, saved.id)
    }
}
