package net.blueshell.api.domain.contribution.persistence

import net.blueshell.api.domain.contribution.web.mapping.asDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
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
            contribution.user = user
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
            contribution.contributionPeriod = period

            val found = persistAndReload(contribution, Contribution::class.java) { it.id }

            assertEquals(period.id, found.contributionPeriodId)
            assertEquals(period.id, found.contributionPeriod.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted contribution`() {
            val user = persist(userFactory.createBasic())
            val period = persist(contributionPeriodFactory.createBasic())
            val contribution = persist(contributionFactory.createBasic().apply {
                this.user = user
                this.contributionPeriod = period
            })

            val dto = contribution.asDto()

            assertEquals(contribution.userId, dto.userId)
            assertEquals(contribution.contributionPeriodId, dto.contributionPeriodId)
        }
    }
}
