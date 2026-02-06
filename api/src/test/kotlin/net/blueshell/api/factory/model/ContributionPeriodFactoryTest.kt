package net.blueshell.api.factory.model

import net.blueshell.api.model.contribution.ContributionPeriod
import org.junit.jupiter.api.Test

class ContributionPeriodFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable contribution period`() {
        val period = contributionPeriodFactory.createBasic()
        val saved = persist(period)
        assertPersisted(ContributionPeriod::class.java, saved.id)
    }
}
