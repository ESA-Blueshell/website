package net.blueshell.api.file.api

import jakarta.persistence.EntityManager
import net.blueshell.api.file.domain.FilesystemBlobStore
import net.blueshell.api.file.domain.InvalidFileException
import net.blueshell.api.file.domain.PublicImageUploadPreparer
import net.blueshell.api.file.domain.ScratchSpace
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.Optional

class FileServiceReceiptTest {
    @TempDir
    lateinit var storage: Path

    private val repository = mock<FileRepository> { on { saveAndFlush(any<File>()) } doAnswer { it.getArgument(0) } }
    private val preparer = mock<PublicImageUploadPreparer>()

    private fun service() =
        FileService(
            repository,
            FilesystemBlobStore(storage.toString()),
            ScratchSpace(storage.toString()),
            mock(),
            mock(),
            mock(),
            mock(),
            preparer,
            mock(),
        ).also {
            FileService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(it, mock<EntityManager>())
        }

    @Test
    fun `a receipt sent as a PDF is stored as it came, and one that only claims to be refused`() {
        whenever(repository.findByPath(any())).thenReturn(Optional.empty())
        val service = service()
        val pdf = "%PDF-1.7\n%stub".toByteArray()

        val stored = service.store(pdf.inputStream(), "bill.pdf", "application/pdf", FileType.RECEIPT, Entities.user(id = 12))

        assertThat(stored.mediaType).isEqualTo("application/pdf")
        assertThat(stored.path).startsWith("receipts/12/").endsWith(".pdf")
        // The same bytes from somebody else are a file of their own.
        val theirs = service.store(pdf.inputStream(), "bill.pdf", "application/pdf", FileType.RECEIPT, Entities.user(id = 13))
        assertThat(theirs.path).isEqualTo(stored.path.replace("receipts/12/", "receipts/13/"))
        assertThat(service.openContent(stored).use { it.readAllBytes() }).isEqualTo(pdf)
        assertThatThrownBy {
            service.store("<html>".byteInputStream(), "bill.pdf", "application/pdf", FileType.RECEIPT, Entities.user(id = 12))
        }.isInstanceOf(InvalidFileException::class.java)
        verifyNoInteractions(preparer)
    }

    @Test
    fun `a stored file is found only as the kind it was asked for`() {
        val receipt = Entities.file(id = 7, type = FileType.RECEIPT)
        whenever(repository.findById(7)).thenReturn(Optional.of(receipt))
        whenever(repository.findById(8)).thenReturn(Optional.empty())
        val service = service()

        assertThat(service.findStored(7, FileType.RECEIPT)).isSameAs(receipt)
        assertThat(service.findStored(7, FileType.EVENT_BANNER)).isNull()
        assertThat(service.findStored(8, FileType.RECEIPT)).isNull()
        assertThat(FileType.RECEIPT.admitsPdf).isTrue()
        assertThat(FileType.EVENT_BANNER.admitsPdf).isFalse()
    }
}
