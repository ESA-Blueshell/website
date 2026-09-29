package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.ExternalTarget
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CohortTargetControllerTest {
    private val catalog = mock<TargetCatalog>()
    private val controller = CohortTargetController(catalog)
    private val list = ExternalTarget(TargetSystem.BREVO, "9", CohortKind.LIST, "Pub quiz", "Projects")

    @Test
    fun `a new list's name and folder are trimmed before Brevo sees them`() {
        whenever(catalog.create(TargetSystem.BREVO, "Pub quiz", "Projects")).thenReturn(list)

        assertThat(controller.create(TargetSystem.BREVO, CreateExternalTargetRequest(" Pub quiz ", " Projects "))).isEqualTo(list)
    }

    @Test
    fun `a rename and a new folder pass their trimmed names on`() {
        whenever(catalog.rename(TargetSystem.BREVO, "9", "Pub quiz 2026")).thenReturn(list.copy(label = "Pub quiz 2026"))
        whenever(catalog.createFolder(TargetSystem.BREVO, "Projects")).thenReturn(listOf("Members", "Projects"))

        val renamed = controller.rename(TargetSystem.BREVO, "9", RenameExternalTargetRequest(" Pub quiz 2026"))
        val folders = controller.createFolder(TargetSystem.BREVO, CreateTargetFolderRequest("Projects "))

        assertThat(renamed.label).isEqualTo("Pub quiz 2026")
        assertThat(folders).containsExactly("Members", "Projects")
    }

    @Test
    fun `archiving answers the list in its new folder, and a delete passes the typed name on`() {
        whenever(catalog.archive(TargetSystem.BREVO, "9")).thenReturn(list.copy(folderLabel = "Archive"))

        assertThat(controller.archive(TargetSystem.BREVO, "9").folderLabel).isEqualTo("Archive")
        controller.delete(TargetSystem.BREVO, "9", DeleteExternalTargetRequest("Pub quiz"))

        verify(catalog).delete(TargetSystem.BREVO, "9", "Pub quiz")
    }
}
