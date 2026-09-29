package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetDeletion
import net.blueshell.api.cohort.persistence.TargetDeletionRepository
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.testsupport.Entities
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
    private val targets: TargetRepository = mock()
    private val strategy = RecordingStrategy()
    private val deletions: TargetDeletionRepository = mock()
    private val actors: ActorProvider = mock { on { currentOrSystem() } doReturn Actor.user(5L, Role.ADMIN) }
    private val catalog = TargetCatalog(TargetStrategies(listOf(strategy)), targets, deletions, actors)

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

        override fun rename(
            external: ExternalTarget,
            name: String,
        ): ExternalTarget = error("not used")

        override fun createFolder(name: String): List<String> = error("not used")
    }

    @Test
    fun `a target's place is read from its system, folder and all`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("10")).thenReturn(
            ExternalTarget(TargetSystem.BREVO, "10", TargetKind.LIST, "Sitecie", "Committees", path = listOf("Brevo", "Committees")),
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

    private fun catalogWith(brevo: TargetStrategy) = TargetCatalog(TargetStrategies(listOf(brevo)), targets, deletions, actors)

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
    fun `moving one list files it and says which target links it, and an unknown list is refused`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "2", TargetKind.LIST, "Members")
        whenever(brevo.resolve("2")).thenReturn(external)
        whenever(brevo.move(external, "Committees")).thenReturn(external.copy(folderLabel = "Committees"))
        whenever(targets.findAllBySystem("BREVO")).thenReturn(listOf(Entities.target(id = 7L, externalId = "2")))

        val moved = catalogWith(brevo).move(TargetSystem.BREVO, "2", "Committees")

        assertThat(moved.linkedTargetId).isEqualTo(7L)
        assertThatThrownBy { catalogWith(brevo).move(TargetSystem.BREVO, "3", "Committees") }
            .hasMessage("No target 3 in BREVO")
    }

    @Test
    fun `a new list is made on the system and comes back unlinked`() {
        val brevo = placingStrategy()
        val made = ExternalTarget(TargetSystem.BREVO, "12", TargetKind.LIST, "Pub quiz", "Committees")
        whenever(brevo.create("Pub quiz", "Committees")).thenReturn(made)

        val created = catalogWith(brevo).create(TargetSystem.BREVO, "Pub quiz", "Committees")

        assertThat(created.linkedTargetId).isNull()
        assertThat(created.externalId).isEqualTo("12")
    }

    @Test
    fun `renaming a linked list renames its cohort too`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "2", TargetKind.LIST, "Members")
        whenever(brevo.resolve("2")).thenReturn(external)
        whenever(brevo.rename(external, "Members 2026")).thenReturn(external.copy(label = "Members 2026"))
        val linked =
            Target("BREVO", TargetKind.LIST, "Members").apply {
                id = 42L
                externalId = "2"
            }
        whenever(targets.findAllBySystem("BREVO")).thenReturn(listOf(linked))
        whenever(targets.findById(42L)).thenReturn(java.util.Optional.of(linked))

        val renamed = catalogWith(brevo).rename(TargetSystem.BREVO, "2", "Members 2026")

        assertThat(renamed.label).isEqualTo("Members 2026")
        assertThat(renamed.linkedTargetId).isEqualTo(42L)
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
        val external = ExternalTarget(TargetSystem.BREVO, "2", TargetKind.LIST, "Paid 2024", "Contribution paid")
        whenever(brevo.resolve("2")).thenReturn(external)
        whenever(brevo.move(external, "Archive")).thenReturn(external.copy(folderLabel = "Archive"))
        val linked =
            Target("BREVO", TargetKind.LIST, "Paid 2024").apply {
                id = 7L
                externalId = "2"
            }
        whenever(targets.findAllBySystem("BREVO")).thenReturn(listOf(linked))

        val archived = catalogWith(brevo).archive(TargetSystem.BREVO, "2")

        verify(brevo).createFolder("Archive")
        assertThat(archived.folderLabel).isEqualTo("Archive")
        assertThat(archived.linkedTargetId).isEqualTo(7L)
    }

    @Test
    fun `deleting an unlinked list by its exact name deletes it and records who did`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "3", TargetKind.LIST, "Old test list")
        whenever(brevo.resolve("3")).thenReturn(external)

        catalogWith(brevo).delete(TargetSystem.BREVO, "3", "Old test list")

        verify(brevo).delete(external)
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
        val external = ExternalTarget(TargetSystem.BREVO, "2", TargetKind.LIST, "Members")
        whenever(brevo.resolve("2")).thenReturn(external)
        val deleting = catalogWith(brevo)

        assertThatThrownBy { deleting.delete(TargetSystem.BREVO, "2", "members") }.isInstanceOf(TargetNameMismatch::class.java)

        whenever(targets.findAllBySystem("BREVO")).thenReturn(
            listOf(
                Target("BREVO", TargetKind.LIST, "Members").apply {
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
