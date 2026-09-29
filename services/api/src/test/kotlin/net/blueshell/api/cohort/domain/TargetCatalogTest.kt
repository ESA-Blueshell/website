package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TargetCatalogTest {
    private val cohorts: CohortRepository = mock()
    private val strategy = RecordingStrategy()
    private val catalog = TargetCatalog(TargetStrategies(listOf(strategy)), cohorts)

    @Test
    fun `descriptors come from registered target strategies`() {
        assertThat(catalog.descriptors()).containsExactly(strategy.descriptor)
    }

    @Test
    fun `search annotates linked cohort ids from active mappings`() {
        val linked =
            Cohort("BREVO", CohortKind.LIST, "Members").apply {
                id = 42L
                externalId = "2"
            }
        whenever(cohorts.findAllBySystem("BREVO")).thenReturn(listOf(linked))

        val results = catalog.search(TargetSystem.BREVO, "members")

        assertThat(strategy.queries).containsExactly("members")
        assertThat(results).extracting<Long?> { it.linkedCohortId }.containsExactly(null, 42L)
    }

    private class RecordingStrategy : TargetStrategy {
        val queries = mutableListOf<String?>()

        override val descriptor =
            TargetDescriptor(
                system = TargetSystem.BREVO,
                kind = CohortKind.LIST,
            )

        override fun catalog(query: String?): List<ExternalTarget> {
            queries += query
            return listOf(target("1", "Guests"), target("2", "Members"))
        }

        override fun members(target: ExternalTarget): List<ExternalMember> = emptyList()

        override fun add(
            target: ExternalTarget,
            externalUserId: String,
        ) = Unit

        override fun remove(
            target: ExternalTarget,
            externalUserId: String,
        ) = Unit

        override fun create(
            label: String,
            folder: String?,
        ): ExternalTarget = error("not used")

        override fun delete(target: ExternalTarget) = Unit

        private fun target(
            id: String,
            label: String,
        ) = ExternalTarget(TargetSystem.BREVO, id, CohortKind.LIST, label)
    }

    @Test
    fun `a target's place is read from its system, folder and all`() {
        whenever(strategy.resolve("10")).thenReturn(
            ExternalTarget(TargetSystem.BREVO, "10", CohortKind.LIST, "Sitecie", "Committees", path = listOf("Brevo", "Committees")),
        )

        assertThat(catalog.placeOf(TargetSystem.BREVO, "10")).isEqualTo(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))
    }

    @Test
    fun `a target's folder is unknown when its system cannot say where it is`() {
        whenever(strategy.resolve("10")).thenThrow(IllegalStateException("Brevo is down"))
        whenever(strategy.resolve("11")).thenReturn(null)

        assertThat(catalog.placeOf(TargetSystem.BREVO, "10")).isEqualTo(TargetPlace(listOf("Brevo"), folderKnown = false))
        assertThat(catalog.placeOf(TargetSystem.BREVO, "11")).isEqualTo(TargetPlace(listOf("Brevo"), folderKnown = false))
    }
}
