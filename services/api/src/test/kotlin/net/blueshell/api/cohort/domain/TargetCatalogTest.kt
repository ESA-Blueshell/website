package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
        val brevo = placingStrategy()
        whenever(brevo.resolve("10")).thenReturn(
            ExternalTarget(TargetSystem.BREVO, "10", CohortKind.LIST, "Sitecie", "Committees", path = listOf("Brevo", "Committees")),
        )

        assertThat(TargetCatalog(TargetStrategies(listOf(brevo)), cohorts).placeOf(TargetSystem.BREVO, "10"))
            .isEqualTo(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))
    }

    @Test
    fun `a target's folder is unknown when its system cannot say where it is`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("10")).thenThrow(IllegalStateException("Brevo is down"))
        whenever(brevo.resolve("11")).thenReturn(null)
        val placing = TargetCatalog(TargetStrategies(listOf(brevo)), cohorts)

        assertThat(placing.placeOf(TargetSystem.BREVO, "10")).isEqualTo(TargetPlace(listOf("Brevo"), folderKnown = false))
        assertThat(placing.placeOf(TargetSystem.BREVO, "11")).isEqualTo(TargetPlace(listOf("Brevo"), folderKnown = false))
    }

    private fun placingStrategy(): TargetStrategy =
        mock<TargetStrategy>().also {
            whenever(it.system).thenReturn(TargetSystem.BREVO)
            whenever(it.descriptor).thenReturn(strategy.descriptor)
        }

    @Test
    fun `a folder is made on the system, which answers every folder`() {
        val brevo = placingStrategy()
        whenever(brevo.createFolder("Archief")).thenReturn(listOf("Archief", "Committees"))

        assertThat(TargetCatalog(TargetStrategies(listOf(brevo)), cohorts).createFolder(TargetSystem.BREVO, "Archief"))
            .containsExactly("Archief", "Committees")
    }

    @Test
    fun `a new list is made on the system and comes back unlinked`() {
        val brevo = placingStrategy()
        val made = ExternalTarget(TargetSystem.BREVO, "12", CohortKind.LIST, "Pub quiz", "Committees")
        whenever(brevo.create("Pub quiz", "Committees")).thenReturn(made)

        val created = TargetCatalog(TargetStrategies(listOf(brevo)), cohorts).create(TargetSystem.BREVO, "Pub quiz", "Committees")

        assertThat(created.linkedCohortId).isNull()
        assertThat(created.externalId).isEqualTo("12")
    }

    @Test
    fun `renaming a linked list renames its cohort too`() {
        val brevo = placingStrategy()
        val target = ExternalTarget(TargetSystem.BREVO, "2", CohortKind.LIST, "Members")
        whenever(brevo.resolve("2")).thenReturn(target)
        whenever(brevo.rename(target, "Members 2026")).thenReturn(target.copy(label = "Members 2026"))
        val linked =
            Cohort("BREVO", CohortKind.LIST, "Members").apply {
                id = 42L
                externalId = "2"
            }
        whenever(cohorts.findAllBySystem("BREVO")).thenReturn(listOf(linked))
        whenever(cohorts.findById(42L)).thenReturn(java.util.Optional.of(linked))

        val renamed = TargetCatalog(TargetStrategies(listOf(brevo)), cohorts).rename(TargetSystem.BREVO, "2", "Members 2026")

        assertThat(renamed.label).isEqualTo("Members 2026")
        assertThat(renamed.linkedCohortId).isEqualTo(42L)
        assertThat(linked.label).isEqualTo("Members 2026")
    }

    @Test
    fun `a refused call says the system's reason and changes nothing here`() {
        val brevo = placingStrategy()
        whenever(brevo.createFolder("Archief")).thenThrow(ContactServiceException("Failed to create folder: Bad Request"))

        assertThatThrownBy { TargetCatalog(TargetStrategies(listOf(brevo)), cohorts).createFolder(TargetSystem.BREVO, "Archief") }
            .isInstanceOf(TargetSystemRefused::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("system" to "Brevo", "reason" to "Failed to create folder: Bad Request"))
    }

    @Test
    fun `renaming a list the system does not have is refused as not found`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("404")).thenReturn(null)

        assertThatThrownBy { TargetCatalog(TargetStrategies(listOf(brevo)), cohorts).rename(TargetSystem.BREVO, "404", "Gone") }
            .isInstanceOf(TargetNotFound::class.java)
    }
}
