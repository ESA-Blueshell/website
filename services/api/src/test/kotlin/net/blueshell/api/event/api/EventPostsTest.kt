package net.blueshell.api.event.api

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.PingedRole
import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.time.Instant

class EventPostsTest {
    private val file: File =
        mock {
            on { path } doReturn "events/lan.webp"
            on { mediaType } doReturn "image/webp"
        }
    private val banner: EventBanner = mock { on { this.file } doReturn file }
    private val event: Event =
        mock {
            on { id } doReturn 42
            on { approved } doReturn true
            on { isSoftDeleted } doReturn false
            on { title } doReturn "LAN party"
            on { startTime } doReturn Instant.parse("2026-10-10T18:00:00Z")
            on { endTime } doReturn Instant.parse("2026-10-10T21:00:00Z")
            on { pingedRoles } doReturn mutableSetOf(PingedRole("901", "Gamers"))
            on { this.banner } doReturn banner
        }
    private val blobs: BlobStore = mock { on { open("events/lan.webp") } doReturn ByteArrayInputStream(byteArrayOf(1, 2)) }

    @Test
    fun `reads an event as the bot posts it, with its pinged roles and its banner's public path`() {
        val events: EventRepository = mock { on { findByIdIncludingDeleted(42) } doReturn event }

        val read = EventPosts(events, blobs).of(42)!!

        assertThat(read.live).isTrue()
        assertThat(read.title).isEqualTo("LAN party")
        assertThat(read.pingedRoleIds).containsExactly("901")
        assertThat(read.bannerPath).isEqualTo("/files/public/events/lan.webp")
    }

    @Test
    fun `reads an event that was deleted, or never approved, as no longer live`() {
        val deleted: Event =
            mock {
                on { id } doReturn 43
                on { approved } doReturn true
                on { isSoftDeleted } doReturn true
                on { title } doReturn "Gone"
                on { startTime } doReturn Instant.EPOCH
                on { endTime } doReturn Instant.EPOCH
                on { pingedRoles } doReturn mutableSetOf()
            }
        val events: EventRepository = mock { on { findByIdIncludingDeleted(43) } doReturn deleted }

        assertThat(EventPosts(events, blobs).of(43)!!.live).isFalse()
        assertThat(EventPosts(events, blobs).of(44)).isNull()
    }

    @Test
    fun `hands over the banner's bytes, and the approved events near a window`() {
        val events: EventRepository =
            mock {
                on { findByIdIncludingDeleted(42) } doReturn event
                on { findApprovedIdsOverlapping(Instant.EPOCH, Instant.MAX) } doReturn listOf(42L)
            }

        val image = EventPosts(events, blobs).bannerOf(42)!!

        assertThat(image.mediaType).isEqualTo("image/webp")
        assertThat(image.bytes).containsExactly(1, 2)
        assertThat(EventPosts(events, blobs).bannerOf(44)).isNull()
        assertThat(EventPosts(events, blobs).approvedOverlapping(Instant.EPOCH, Instant.MAX)).containsExactly(42L)
    }

    @Test
    fun `hands over the widest rendition a Discord event's cover takes, not the master`() {
        val small: File =
            mock {
                on { renditionWidth } doReturn 800
                on { path } doReturn "events/lan-800.webp"
                on { mediaType } doReturn "image/webp"
            }
        val cover: File =
            mock {
                on { renditionWidth } doReturn 1600
                on { path } doReturn "events/lan-1600.webp"
                on { mediaType } doReturn "image/webp"
            }
        val huge: File = mock { on { renditionWidth } doReturn 3200 }
        whenever(file.renditions).thenReturn(listOf(small, cover, huge))
        whenever(blobs.open("events/lan-1600.webp")).thenReturn(ByteArrayInputStream(byteArrayOf(9)))
        val events: EventRepository = mock { on { findByIdIncludingDeleted(42) } doReturn event }

        assertThat(EventPosts(events, blobs).bannerOf(42)!!.bytes).containsExactly(9)
    }
}

