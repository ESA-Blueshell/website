package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class PingerPaintServiceTest {
    private val repository = mockk<PingerPaintRepository>()
    private val pictures = mockk<StoredPictures>()
    private val service = PingerPaintService(repository, pictures)

    private fun row(imagePath: String? = null) =
        PingerPaint(ratePps = 128, originX = 0, originY = 0, width = 10, height = 10, imagePath = imagePath)

    @Test
    fun `update saves the box and builds the image url from the stored path`() {
        every { repository.findById(1L) } returns Optional.of(row())
        every { repository.save(any()) } answers { firstArg() }
        val file = Entities.file(type = FileType.PINGER_PAINT, path = "pinger-paint/a.webp")
        every { pictures.of("pinger-paint/a.webp", FileType.PINGER_PAINT) } returns file

        val view = service.update("2001:db8::/64", 256, 100, 200, 800, 600, "pinger-paint/a.webp", siteCieEnabled = false)

        assertThat(view.ratePps).isEqualTo(256)
        assertThat(view.originX).isEqualTo(100)
        assertThat(view.imageUrl).isEqualTo("/files/public/pinger-paint/a.webp")
        assertThat(view.siteCieEnabled).isFalse()
        verify { repository.save(any()) }
    }

    @Test
    fun `update turns SiteCie back on`() {
        every { repository.findById(1L) } returns Optional.of(row().apply { siteCieEnabled = false })
        every { repository.save(any()) } answers { firstArg() }
        every { pictures.of(null, FileType.PINGER_PAINT) } returns null

        val view = service.update(null, 128, 0, 0, 10, 10, null, siteCieEnabled = true)

        assertThat(view.siteCieEnabled).isTrue()
    }

    @Test
    fun `update refuses a box that runs off the canvas`() {
        assertThatThrownBy { service.update(null, 128, 3800, 0, 800, 10, null, siteCieEnabled = true) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `update refuses a rate outside the cap`() {
        assertThatThrownBy { service.update(null, 0, 0, 0, 10, 10, null, siteCieEnabled = true) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `current reads the row and its image`() {
        every { repository.findById(1L) } returns Optional.of(row(imagePath = "pinger-paint/x.webp"))

        val view = service.current()

        assertThat(view.imageUrl).isEqualTo("/files/public/pinger-paint/x.webp")
    }

    @Test
    fun `current falls back to the defaults when the row is missing`() {
        every { repository.findById(1L) } returns Optional.empty()

        val view = service.current()

        assertThat(view.ratePps).isEqualTo(128)
        assertThat(view.imageUrl).isNull()
        assertThat(view.siteCieEnabled).isTrue()
    }
}
