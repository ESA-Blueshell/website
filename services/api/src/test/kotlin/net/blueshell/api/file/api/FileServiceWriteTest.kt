package net.blueshell.api.file.api

import jakarta.persistence.EntityManager
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.InvocationTargetException
import java.util.Optional

/**
 * The reads and the two ways a stored file is saved. Storing itself needs blobs, scratch space
 * and renditions, so the saves are reached directly here and end to end in `FileControllerIT`.
 */
class FileServiceWriteTest {
    private val manager = mock<EntityManager>()
    private val repository = mock<FileRepository> { on { saveAndFlush(any<File>()) } doAnswer { it.getArgument(0) } }
    private val service =
        FileService(repository, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock()).also {
            FileService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(it, manager)
        }

    private fun save(
        how: String,
        file: File,
    ): File =
        try {
            FileService::class.java
                .getDeclaredMethod(how, File::class.java)
                .apply { isAccessible = true }
                .invoke(service, file) as File
        } catch (thrown: InvocationTargetException) {
            throw thrown.targetException
        }

    @Test
    fun `reads a file, says whether one exists, and refuses one that is not there`() {
        val file = mock<File>()
        whenever(repository.findById(1)).thenReturn(Optional.of(file))
        whenever(repository.findById(2)).thenReturn(Optional.empty())
        whenever(repository.existsById(1)).thenReturn(true)

        assertThat(service.findById(1)).isSameAs(file)
        assertThat(service.existsById(1)).isTrue()
        assertThatThrownBy { service.findById(2) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a new file is written and a stored one written back, each read back after`() {
        val fresh = mock<File>()
        val stored = mock<File>().also { whenever(it.id).thenReturn(3) }
        whenever(repository.existsById(3)).thenReturn(true)

        assertThat(save("written", fresh)).isSameAs(fresh)
        assertThat(save("rewritten", stored)).isSameAs(stored)

        verify(manager, times(1)).refresh(fresh)
        verify(manager, times(1)).refresh(stored)
    }

    @Test
    fun `a file the database no longer has is not written back`() {
        val lost = mock<File>().also { whenever(it.id).thenReturn(4) }

        assertThatThrownBy { save("rewritten", lost) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
