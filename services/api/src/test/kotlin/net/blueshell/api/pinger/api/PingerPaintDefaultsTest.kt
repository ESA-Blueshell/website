package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import net.blueshell.api.file.api.FileService
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.persistence.PingerPlacementRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.util.Optional

class PingerPaintDefaultsTest {
    private val paints = mockk<PingerPaintRepository>(relaxUnitFun = true)
    private val placements = mockk<PingerPlacementRepository>(relaxUnitFun = true)
    private val files = mockk<FileService>()
    private val users = mockk<UserService>()
    private val defaults = PingerPaintDefaults(paints, placements, files, users)

    @Test
    fun `the first boot seeds one placement of the default image and marks the row done`() {
        val row = PingerPaint(ratePps = 128, defaultsInitialized = false)
        every { paints.findById(1L) } returns Optional.of(row)
        every { paints.save(any()) } answers { firstArg() }
        every { users.findByUsername("system") } returns Entities.user()
        every {
            files.store(any<InputStream>(), any(), any(), FileType.PINGER_PAINT, any())
        } returns Entities.file(type = FileType.PINGER_PAINT, path = "pinger-paint/logo.webp")
        val saved = slot<PingerPlacement>()
        every { placements.save(capture(saved)) } answers { firstArg() }

        defaults.apply()

        assertThat(saved.captured.imagePath).isEqualTo("pinger-paint/logo.webp")
        assertThat(saved.captured.originX).isEqualTo(1470)
        assertThat(saved.captured.width).isEqualTo(900)
        assertThat(row.prefix).isNotBlank()
        assertThat(row.defaultsInitialized).isTrue()
        verify { paints.save(row) }
    }

    @Test
    fun `an already-initialised row is left alone, so an admin edit survives a restart`() {
        every { paints.findById(1L) } returns Optional.of(PingerPaint(ratePps = 128, defaultsInitialized = true))

        defaults.apply()

        verify(exactly = 0) { placements.save(any()) }
        verify(exactly = 0) { files.store(any<InputStream>(), any(), any(), any(), any()) }
        verify(exactly = 0) { paints.save(any()) }
    }

    @Test
    fun `the ready event seeds the row`() {
        val row = PingerPaint(ratePps = 128, defaultsInitialized = false)
        every { paints.findById(1L) } returns Optional.of(row)
        every { paints.save(any()) } answers { firstArg() }
        every { users.findByUsername("system") } returns Entities.user()
        every {
            files.store(any<InputStream>(), any(), any(), FileType.PINGER_PAINT, any())
        } returns Entities.file(type = FileType.PINGER_PAINT, path = "pinger-paint/logo.webp")
        every { placements.save(any()) } answers { firstArg() }

        defaults.onReady()

        verify { placements.save(any()) }
        assertThat(row.defaultsInitialized).isTrue()
    }

    @Test
    fun `a seeding failure on the ready event is swallowed, so the start is never held up`() {
        every { paints.findById(1L) } throws IllegalStateException("db down")

        defaults.onReady()

        verify(exactly = 0) { placements.save(any()) }
    }

    @Test
    fun `a missing shipped image is swallowed on the ready event rather than held up`() {
        val spied = spyk(defaults)
        every { spied.defaultImageStream() } returns null
        every { paints.findById(1L) } returns Optional.of(PingerPaint(ratePps = 128, defaultsInitialized = false))
        every { users.findByUsername("system") } returns Entities.user()

        spied.onReady()

        verify(exactly = 0) { placements.save(any()) }
    }

    @Test
    fun `no site account leaves the box unseeded but still marks the row done`() {
        val row = PingerPaint(ratePps = 128, defaultsInitialized = false)
        every { paints.findById(1L) } returns Optional.of(row)
        every { paints.save(any()) } answers { firstArg() }
        every { users.findByUsername("system") } throws IllegalStateException("no such account")

        defaults.apply()

        verify(exactly = 0) { placements.save(any()) }
        verify(exactly = 0) { files.store(any<InputStream>(), any(), any(), any(), any()) }
        assertThat(row.defaultsInitialized).isTrue()
    }
}
