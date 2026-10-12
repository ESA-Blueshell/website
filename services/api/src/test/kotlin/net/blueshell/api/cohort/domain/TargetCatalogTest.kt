package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TargetCatalogTest {
    private val targets: TargetRepository = mock()
    private val strategy = RecordingStrategy()
    private val catalog = TargetCatalog(TargetStrategies(listOf(strategy)), targets)

    @Test
    fun `descriptors come from registered target strategies`() {
        assertThat(catalog.descriptors()).containsExactly(strategy.descriptor)
    }

    @Test
    fun `search annotates linked cohort ids from active mappings`() {
        val linked =
            Target("BREVO", TargetKind.LIST, "Members").apply {
                id = 42L
                externalId = "2"
            }
        whenever(targets.findAllBySystem("BREVO")).thenReturn(listOf(linked))

        val results = catalog.search(TargetSystem.BREVO, "members")

        assertThat(strategy.queries).containsExactly("members")
        assertThat(results).extracting<Long?> { it.linkedTargetId }.containsExactly(null, 42L)
    }

    private class RecordingStrategy : TargetStrategy {
        val queries = mutableListOf<String?>()

        override val descriptor =
            TargetDescriptor(
                system = TargetSystem.BREVO,
                kind = TargetKind.LIST,
            )

        override fun catalog(query: String?): List<ExternalTarget> {
            queries += query
            return listOf(external("1", "Guests"), external("2", "Members"))
        }

        override fun members(external: ExternalTarget): List<ExternalMember> = emptyList()

        override fun add(
            external: ExternalTarget,
            externalUserId: String,
        ) = Unit

        override fun remove(
            external: ExternalTarget,
            externalUserId: String,
        ) = Unit

        override fun create(
            label: String,
            folder: String?,
        ): ExternalTarget = error("not used")

        override fun delete(external: ExternalTarget) = Unit

        private fun external(
            id: String,
            label: String,
        ) = ExternalTarget(TargetSystem.BREVO, id, TargetKind.LIST, label)
    }
}
