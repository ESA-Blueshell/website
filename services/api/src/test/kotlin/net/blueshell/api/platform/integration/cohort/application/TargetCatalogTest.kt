package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.out.ExternalMember
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.cohort.port.out.TargetCapability
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.platform.integration.cohort.port.out.TargetStrategy
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
        val linked = Cohort("BREVO", CohortKind.LIST, "Members").apply {
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

        override val descriptor = TargetDescriptor(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            systemLabel = "Brevo",
            targetLabel = "Brevo list",
            idLabel = "List id",
            folderLabel = "Folder",
            capabilities = setOf(TargetCapability.CATALOG),
        )

        override fun catalog(query: String?): List<ExternalTarget> {
            queries += query
            return listOf(target("1", "Guests"), target("2", "Members"))
        }

        override fun members(target: ExternalTarget): List<ExternalMember> = emptyList()
        override fun add(target: ExternalTarget, externalUserId: String) = Unit
        override fun remove(target: ExternalTarget, externalUserId: String) = Unit
        override fun create(label: String, folder: String?): ExternalTarget = error("not used")
        override fun delete(target: ExternalTarget) = Unit

        private fun target(id: String, label: String) =
            ExternalTarget(TargetSystem.BREVO, id, CohortKind.LIST, label)
    }
}
