package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.FolderMerge
import net.blueshell.api.cohort.domain.FolderState
import net.blueshell.api.cohort.domain.TargetFolders
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TargetFolderControllerTest {
    private val folders = mock<TargetFolders>()
    private val controller = TargetFolderController(folders)

    @Test
    fun `folders are read with what they hold, merged where they share a name, and removed by id`() {
        whenever(folders.states(TargetSystem.BREVO)).thenReturn(listOf(FolderState("3", "Committees", 2)))
        whenever(folders.merge(TargetSystem.BREVO)).thenReturn(FolderMerge(removed = 1, moved = 2))

        assertThat(controller.states(TargetSystem.BREVO)).containsExactly(FolderState("3", "Committees", 2))
        assertThat(controller.merge(TargetSystem.BREVO)).isEqualTo(FolderMerge(1, 2))
        controller.remove(TargetSystem.BREVO, "5")
        verify(folders).remove(TargetSystem.BREVO, "5")
    }
}
