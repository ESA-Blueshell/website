package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CohortLinkedRolesTest {
    @Test
    fun `answers the Discord roles linked to a cohort, never a target not made yet`() {
        val targets =
            mock<TargetRepository> {
                on { findAllBySystem("DISCORD") } doReturn
                    listOf(
                        Entities.target(id = 1L, cohortId = 1L, externalId = "900"),
                        Entities.target(id = 2L, cohortId = 2L, externalId = null),
                    )
            }

        assertThat(CohortLinkedRoles(targets, CohortTargetIds(targets)).ids()).containsExactly("900")
    }
}
