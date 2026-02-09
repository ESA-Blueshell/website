package net.blueshell.api.contribution.domain.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.contribution.domain.model.Contribution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ContributionModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists user relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val contribution = contributionFactory.createBasic()
            contribution.user = user
            contribution.contributionPeriod = period

            val found = persistAndReload(contribution, Contribution::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val contribution = contributionFactory.createBasic()
            contribution.userId = user.id!!
            contribution.contributionPeriod = period

            val found = persistAndReload(contribution, Contribution::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists contribution period relation when setting entity`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val contribution = contributionFactory.createBasic()
            contribution.user = user
            contribution.contributionPeriod = period

            val found = persistAndReload(contribution, Contribution::class.java) { it.id }

            assertEquals(period.id, found.contributionPeriodId)
            assertEquals(period.id, found.contributionPeriod.id)
        }

        @Test
        fun `persists contribution period relation when setting id`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())

            val contribution = contributionFactory.createBasic()
            contribution.user = user
            contribution.contributionPeriodId = period.id!!

            val found = persistAndReload(contribution, Contribution::class.java) { it.id }

            assertEquals(period.id, found.contributionPeriodId)
            assertEquals(period.id, found.contributionPeriod.id)
        }
    }
}
