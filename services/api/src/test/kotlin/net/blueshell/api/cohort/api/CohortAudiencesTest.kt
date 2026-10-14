package net.blueshell.api.cohort.api

import net.blueshell.api.cohort.domain.CohortDefinition
import net.blueshell.api.cohort.domain.CohortDefinitionRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CohortAudiencesTest {
    @Test
    fun `names every cohort by its key and label, and who is in one`() {
        val registry: CohortDefinitionRegistry = mock()
        val members: CohortDefinition =
            mock {
                on { key } doReturn "CURRENT_MEMBERS"
                on { label } doReturn "Members"
            }
        val active: CohortDefinition =
            mock {
                on { key } doReturn "ACTIVISTS"
                on { label } doReturn "Activists"
            }
        whenever(registry.all()).thenReturn(listOf(members, active))
        whenever(registry.byKey("CURRENT_MEMBERS")).thenReturn(members)
        whenever(registry.membersOf(members)).thenReturn(setOf(1, 2))

        val audiences = CohortAudiences(registry)
        assertThat(audiences.all()).containsExactly(Audience("ACTIVISTS", "Activists"), Audience("CURRENT_MEMBERS", "Members"))
        assertThat(audiences.membersOf("CURRENT_MEMBERS")).containsExactly(1, 2)
        assertThat(audiences.membersOf("NONE")).isEmpty()
    }
}
