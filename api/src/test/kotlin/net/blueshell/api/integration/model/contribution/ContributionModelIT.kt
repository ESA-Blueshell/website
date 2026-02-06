package net.blueshell.api.integration.model.contribution

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContributionModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val contribution = contributionFactory.createBasic()
            contribution.user = user
            contribution.contributionPeriod = period

            val found = persistAndReload(contribution, Contribution::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(period.id, found.contributionPeriodId)
        }
    }
}
