package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class ContributionPeriodFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable contribution period`() {
        val period = contributionPeriodFactory.createBasic()
        val saved = persist(period)
        assertPersisted(net.blueshell.api.model.contribution.ContributionPeriod::class.java, saved.id)
    }
}
