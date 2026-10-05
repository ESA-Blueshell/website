package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TargetFoldersTest {
    private val keeper: FolderKeeper = mock()
    private val brevo: TargetStrategy =
        mock {
            whenever(it.system).thenReturn(TargetSystem.BREVO)
            whenever(it.descriptor).thenReturn(TargetDescriptor(TargetSystem.BREVO, TargetKind.LIST))
            whenever(it.folderKeeper).thenReturn(keeper)
        }
    private val discord: TargetStrategy =
        mock {
            whenever(it.system).thenReturn(TargetSystem.DISCORD)
            whenever(it.descriptor).thenReturn(TargetDescriptor(TargetSystem.DISCORD, TargetKind.ROLE))
        }
    private val folders = TargetFolders(TargetStrategies(listOf(brevo, discord)))

    @Test
    fun `folders are read, merged and removed by the system that keeps them`() {
        whenever(keeper.states()).thenReturn(listOf(FolderState("3", "Committees", 2)))
        whenever(keeper.merge()).thenReturn(FolderMerge(removed = 1, moved = 2))

        assertThat(folders.states(TargetSystem.BREVO)).containsExactly(FolderState("3", "Committees", 2))
        assertThat(folders.merge(TargetSystem.BREVO).let { it.removed to it.moved }).isEqualTo(1 to 2)
        folders.remove(TargetSystem.BREVO, "5")
        verify(keeper).remove("5")
    }

    @Test
    fun `the system's refusal is said, and a system without folders has none to say or change`() {
        whenever(keeper.remove("9")).thenThrow(ContactServiceException("The folder still holds lists"))

        assertThatThrownBy { folders.remove(TargetSystem.BREVO, "9") }
            .isInstanceOf(TargetSystemRefused::class.java)
            .extracting("facts")
            .isEqualTo(mapOf("system" to "Brevo", "reason" to "The folder still holds lists"))
        assertThat(folders.states(TargetSystem.DISCORD)).isEmpty()
        assertThatThrownBy { folders.merge(TargetSystem.DISCORD) }.isInstanceOf(TargetSystemRefused::class.java)
        assertThatThrownBy { folders.remove(TargetSystem.DISCORD, "1") }.isInstanceOf(TargetSystemRefused::class.java)
    }
}
