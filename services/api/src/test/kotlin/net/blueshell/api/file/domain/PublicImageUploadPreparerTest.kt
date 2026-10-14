package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.Base64

class PublicImageUploadPreparerTest {
    @TempDir
    private lateinit var directory: Path

    private val converter: ImageConverter = mock()
    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }
    private val preparer: PublicImageUploadPreparer by lazy { PublicImageUploadPreparer(converter) }

    /** A lossless WebP of 40 by 25 pixels. */
    private val smallWebp = Base64.getDecoder().decode("UklGRioAAABXRUJQVlA4TB4AAAAvJwAGALmM6H/sIqL/ASEBoft/WlXmYBpiAoCrui8=")

    @Test
    fun `a kind stored as it was sent is not prepared`() {
        assertThat(preparer.prepare(scratch.cut(".pdf"), FileType.DOCUMENT, "application/pdf")).isNull()
        verifyNoInteractions(converter)
    }

    @Test
    fun `a WebP already under the ceiling is the master as it is`() {
        val source = scratch.cut(".webp").also { it.path.toFile().writeBytes(smallWebp) }

        val prepared = preparer.prepare(source, FileType.EVENT_BANNER, "image/webp")

        assertThat(prepared?.bytes).isSameAs(source)
        assertThat(prepared?.width to prepared?.height).isEqualTo(40 to 25)
        verifyNoInteractions(converter)
    }

    @Test
    fun `anything else is converted into a master`() {
        val source = scratch.cut(".png")
        val master = scratch.cut(".webp")
        whenever(converter.master(any(), any(), any())).thenReturn(ImageConverter.Converted(master, ImageDimensions.Size(800, 600)))

        val prepared = preparer.prepare(source, FileType.EVENT_BANNER, "image/png")

        assertThat(prepared).isEqualTo(PublicImageUploadPreparer.Prepared(master, "image/webp", 800, 600))
        verify(converter).master(source, "image/png", FileType.EVENT_BANNER)
    }
}
