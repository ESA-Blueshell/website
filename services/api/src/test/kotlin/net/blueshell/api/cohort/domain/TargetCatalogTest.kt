package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

        assertThat(TargetCatalog(TargetStrategies(listOf(brevo)), targets).placeOf(TargetSystem.BREVO, "10"))
            .isEqualTo(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))
    }

    @Test
    fun `a target's folder is unknown when its system cannot say where it is`() {
        val brevo = placingStrategy()
        whenever(brevo.resolve("10")).thenThrow(IllegalStateException("Brevo is down"))
        whenever(brevo.resolve("11")).thenReturn(null)
        val placing = TargetCatalog(TargetStrategies(listOf(brevo)), targets)

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

        assertThat(TargetCatalog(TargetStrategies(listOf(brevo)), targets).createFolder(TargetSystem.BREVO, "Archief"))
            .containsExactly("Archief", "Committees")
    }

    @Test
    fun `a new list is made on the system and comes back unlinked`() {
        val brevo = placingStrategy()
        val made = ExternalTarget(TargetSystem.BREVO, "12", TargetKind.LIST, "Pub quiz", "Committees")
        whenever(brevo.create("Pub quiz", "Committees")).thenReturn(made)

        val created = TargetCatalog(TargetStrategies(listOf(brevo)), targets).create(TargetSystem.BREVO, "Pub quiz", "Committees")

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

        val renamed = TargetCatalog(TargetStrategies(listOf(brevo)), targets).rename(TargetSystem.BREVO, "2", "Members 2026")

        assertThat(renamed.label).isEqualTo("Members 2026")
        assertThat(renamed.linkedTargetId).isEqualTo(42L)
        assertThat(linked.label).isEqualTo("Members 2026")
    }

    @Test
    fun `a refused call says the system's reason and changes nothing here`() {
        val brevo = placingStrategy()
        whenever(brevo.createFolder("Archief")).thenThrow(ContactServiceException("Failed to create folder: Bad Request"))

        assertThatThrownBy { TargetCatalog(TargetStrategies(listOf(brevo)), targets).createFolder(TargetSystem.BREVO, "Archief") }
            .isInstanceOf(TargetSystemRefused::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("system" to "Brevo", "reason" to "Failed to create folder: Bad Request"))
    }

    @Test
    fun `a rename the system refuses, on the read or on the write, leaves the target as it was`() {
        val brevo = placingStrategy()
        val external = ExternalTarget(TargetSystem.BREVO, "2", TargetKind.LIST, "Members")
        val catalog = TargetCatalog(TargetStrategies(listOf(brevo)), targets)
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

        assertThatThrownBy { TargetCatalog(TargetStrategies(listOf(brevo)), targets).rename(TargetSystem.BREVO, "404", "Gone") }
            .isInstanceOf(TargetNotFound::class.java)
    }
}
