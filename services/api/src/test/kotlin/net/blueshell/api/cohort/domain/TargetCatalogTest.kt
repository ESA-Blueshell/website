package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.TargetDeletion
import net.blueshell.api.cohort.persistence.TargetDeletionRepository
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TargetCatalogTest {
    private val cohorts: CohortRepository = mock()
    private val strategy = RecordingStrategy()
    private val deletions: TargetDeletionRepository = mock()
    private val actors: ActorProvider = mock { on { currentOrSystem() } doReturn Actor.user(5L, Role.ADMIN) }
    private val catalog = TargetCatalog(TargetStrategies(listOf(strategy)), cohorts, deletions, actors)

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

        override fun rename(
            target: ExternalTarget,
            name: String,
        ): ExternalTarget = error("not used")

        override fun createFolder(name: String): List<String> = error("not used")
    }

    @Test
    fun `a target's place is read from its system, folder and all`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("10")).thenReturn(
            ExternalTarget(TargetSystem.BREVO, "10", CohortKind.LIST, "Sitecie", "Committees", path = listOf("Brevo", "Committees")),
        )

        assertThat(catalogWith(brevo).placeOf(TargetSystem.BREVO, "10"))
            .isEqualTo(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))
    }

    @Test
    fun `a target's folder is unknown when its system cannot say where it is`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("10")).thenThrow(IllegalStateException("Brevo is down"))
        whenever(brevo.resolve("11")).thenReturn(null)
        val placing = catalogWith(brevo)

        assertThat(placing.placeOf(TargetSystem.BREVO, "10")).isEqualTo(TargetPlace(listOf("Brevo"), folderKnown = false))
        assertThat(placing.placeOf(TargetSystem.BREVO, "11")).isEqualTo(TargetPlace(listOf("Brevo"), folderKnown = false))
    }

    private fun catalogWith(brevo: TargetStrategy) = TargetCatalog(TargetStrategies(listOf(brevo)), cohorts, deletions, actors)

    private fun placingStrategy(): TargetStrategy =
        mock<TargetStrategy>().also {
            whenever(it.system).thenReturn(TargetSystem.BREVO)
            whenever(it.descriptor).thenReturn(strategy.descriptor)
        }

    @Test
    fun `a folder is made on the system, which answers every folder`() {
        val brevo = placingStrategy()
        whenever(brevo.createFolder("Archief")).thenReturn(listOf("Archief", "Committees"))

        assertThat(catalogWith(brevo).createFolder(TargetSystem.BREVO, "Archief"))
            .containsExactly("Archief", "Committees")
    }

    @Test
    fun `a new list is made on the system and comes back unlinked`() {
        val brevo = placingStrategy()
        val made = ExternalTarget(TargetSystem.BREVO, "12", CohortKind.LIST, "Pub quiz", "Committees")
        whenever(brevo.create("Pub quiz", "Committees")).thenReturn(made)

        val created = catalogWith(brevo).create(TargetSystem.BREVO, "Pub quiz", "Committees")

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

        val renamed = catalogWith(brevo).rename(TargetSystem.BREVO, "2", "Members 2026")

        assertThat(renamed.label).isEqualTo("Members 2026")
        assertThat(renamed.linkedCohortId).isEqualTo(42L)
        assertThat(linked.label).isEqualTo("Members 2026")
    }

    @Test
    fun `a refused call says the system's reason and changes nothing here`() {
        val brevo = placingStrategy()
        whenever(brevo.createFolder("Archief")).thenThrow(ContactServiceException("Failed to create folder: Bad Request"))

        assertThatThrownBy { catalogWith(brevo).createFolder(TargetSystem.BREVO, "Archief") }
            .isInstanceOf(TargetSystemRefused::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("system" to "Brevo", "reason" to "Failed to create folder: Bad Request"))
    }

    @Test
    fun `renaming a list the system does not have is refused as not found`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("404")).thenReturn(null)

        assertThatThrownBy { catalogWith(brevo).rename(TargetSystem.BREVO, "404", "Gone") }
            .isInstanceOf(TargetNotFound::class.java)
    }

    @Test
    fun `archiving files a list in the archive folder, made first, and keeps its link`() {
        val brevo = placingStrategy()
        val target = ExternalTarget(TargetSystem.BREVO, "2", CohortKind.LIST, "Paid 2024", "Contribution paid")
        whenever(brevo.resolve("2")).thenReturn(target)
        whenever(brevo.move(target, "Archive")).thenReturn(target.copy(folderLabel = "Archive"))
        val linked =
            Cohort("BREVO", CohortKind.LIST, "Paid 2024").apply {
                id = 7L
                externalId = "2"
            }
        whenever(cohorts.findAllBySystem("BREVO")).thenReturn(listOf(linked))

        val archived = catalogWith(brevo).archive(TargetSystem.BREVO, "2")

        verify(brevo).createFolder("Archive")
        assertThat(archived.folderLabel).isEqualTo("Archive")
        assertThat(archived.linkedCohortId).isEqualTo(7L)
    }

    @Test
    fun `deleting an unlinked list by its exact name deletes it and records who did`() {
        val brevo = placingStrategy()
        val target = ExternalTarget(TargetSystem.BREVO, "3", CohortKind.LIST, "Old test list")
        whenever(brevo.resolve("3")).thenReturn(target)

        catalogWith(brevo).delete(TargetSystem.BREVO, "3", "Old test list")

        verify(brevo).delete(target)
        val recorded = argumentCaptor<TargetDeletion>()
        verify(deletions).save(recorded.capture())
        with(recorded.firstValue) {
            assertThat(listOf(system, externalId, name)).containsExactly("BREVO", "3", "Old test list")
            assertThat(deletedBy).isEqualTo(5L)
            assertThat(deletedAt).isNotNull()
        }
    }

    @Test
    fun `a linked list or a mistyped name is refused, and nothing is deleted`() {
        val brevo = placingStrategy()
        val target = ExternalTarget(TargetSystem.BREVO, "2", CohortKind.LIST, "Members")
        whenever(brevo.resolve("2")).thenReturn(target)
        val deleting = catalogWith(brevo)

        assertThatThrownBy { deleting.delete(TargetSystem.BREVO, "2", "members") }.isInstanceOf(TargetNameMismatch::class.java)

        whenever(cohorts.findAllBySystem("BREVO")).thenReturn(
            listOf(
                Cohort("BREVO", CohortKind.LIST, "Members").apply {
                    id = 1L
                    externalId = "2"
                },
            ),
        )
        assertThatThrownBy { deleting.delete(TargetSystem.BREVO, "2", "Members") }.isInstanceOf(TargetStillLinked::class.java)

        verify(brevo, never()).delete(any())
        verify(deletions, never()).save(any<TargetDeletion>())
    }
}
