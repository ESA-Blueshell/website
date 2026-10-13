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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class TargetCatalogTest {
    private val targets: TargetRepository = mock()
    private val strategy = RecordingStrategy()
    private val deletions: TargetDeletionRepository = mock { on { save(any<TargetDeletion>()) } doAnswer { it.getArgument(0) } }
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
    fun `a new list is made on the system and comes back unlinked`() {
        val brevo = placingStrategy()
        val made = ExternalTarget(TargetSystem.BREVO, "12", TargetKind.LIST, "Pub quiz", "Committees")
        whenever(brevo.create("Pub quiz", "Committees")).thenReturn(made)

        val created = catalogWith(brevo).create(TargetSystem.BREVO, "Pub quiz", "Committees")

        assertThat(created.linkedTargetId).isNull()
        assertThat(created.externalId).isEqualTo("12")
    }

    @Test
    fun `renaming a linked list renames its target too`() {
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
    fun `a rename the system refuses, on the read or on the write, leaves the target as it was`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "2", TargetKind.LIST, "Members")
        val catalog = catalogWith(brevo)
        whenever(brevo.resolve("2")).thenThrow(ContactServiceException("Brevo did not answer")).thenReturn(external)
        whenever(brevo.rename(external, "Members 2026")).thenThrow(ContactServiceException("Failed to rename list: Name is taken"))

        val reasons =
            (1..2).map {
                catchThrowableOfType(TargetSystemRefused::class.java) { catalog.rename(TargetSystem.BREVO, "2", "Members 2026") }
                    .facts["reason"]
            }

        assertThat(reasons).containsExactly("Brevo did not answer", "Failed to rename list: Name is taken")
        verify(targets, never()).save(any())
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

    @Test
    fun `a delete is recorded before the list goes, so one that cannot be recorded deletes nothing`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("3")).thenReturn(ExternalTarget(TargetSystem.BREVO, "3", TargetKind.LIST, "Old test list"))
        whenever(deletions.save(any<TargetDeletion>())).thenThrow(IllegalStateException("the database is down"))

        assertThatThrownBy { catalogWith(brevo).delete(TargetSystem.BREVO, "3", "Old test list") }
            .isInstanceOf(IllegalStateException::class.java)

        verify(brevo, never()).delete(any())
    }

    @Test
    fun `a delete the system refuses leaves no record`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "3", TargetKind.LIST, "Old test list")
        whenever(brevo.resolve("3")).thenReturn(external)
        whenever(brevo.delete(external)).thenThrow(ContactServiceException("Failed to delete list: List is used by a campaign"))
        val recorded = TargetDeletion("BREVO", "3", "Old test list", 5L, Instant.parse("2026-10-13T08:00:00Z"))
        whenever(deletions.save(any<TargetDeletion>())).thenReturn(recorded)

        assertThatThrownBy { catalogWith(brevo).delete(TargetSystem.BREVO, "3", "Old test list") }
            .isInstanceOf(TargetSystemRefused::class.java)

        verify(deletions).delete(recorded)
    }

    @Test
    fun `a delete that fails keeps its record when the list is gone or cannot be read`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "3", TargetKind.LIST, "Old test list")
        whenever(brevo.resolve("3"))
            .thenReturn(external)
            .thenReturn(null)
            .thenReturn(external)
            .thenThrow(ContactServiceException("Brevo did not answer"))
        whenever(brevo.delete(external)).thenThrow(ContactServiceException("Failed to delete list: Brevo did not answer"))

        repeat(2) {
            assertThatThrownBy { catalogWith(brevo).delete(TargetSystem.BREVO, "3", "Old test list") }
                .isInstanceOf(TargetSystemRefused::class.java)
        }

        verify(deletions, never()).delete(any<TargetDeletion>())
    }

    @Test
    fun `a linked list is refused under any spelling of its id, and the delete is recorded under the system's own`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "7", TargetKind.LIST, "Members")
        whenever(brevo.resolve("07")).thenReturn(external)
        val deleting = catalogWith(brevo)
        whenever(targets.findAllBySystem("BREVO")).thenReturn(
            listOf(
                Target("BREVO", TargetKind.LIST, "Members").apply {
                    id = 1L
                    externalId = "7"
                },
            ),
        )

        assertThatThrownBy { deleting.delete(TargetSystem.BREVO, "07", "Members") }.isInstanceOf(TargetStillLinked::class.java)
        verify(brevo, never()).delete(any())

        whenever(targets.findAllBySystem("BREVO")).thenReturn(emptyList())
        deleting.delete(TargetSystem.BREVO, "07", "Members")

        val recorded = argumentCaptor<TargetDeletion>()
        verify(deletions).save(recorded.capture())
        assertThat(recorded.firstValue.externalId).isEqualTo("7")
    }
}
