package net.blueshell.api.factory.model.contribution

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.contribution.domain.model.ContributionPeriod
import org.junit.jupiter.api.Test

class ContributionPeriodFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable contribution period`() {
        val period = contributionPeriodFactory.createBasic()
        val saved = persist(period)
        assertPersisted(ContributionPeriod::class.java, saved.id)
    }
}
