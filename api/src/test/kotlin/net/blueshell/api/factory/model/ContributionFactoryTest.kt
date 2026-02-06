package net.blueshell.api.factory.model

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
        assertPersisted(net.blueshell.api.model.contribution.Contribution::class.java, saved.id)
    }
}
