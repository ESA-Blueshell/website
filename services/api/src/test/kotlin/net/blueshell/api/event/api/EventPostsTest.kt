package net.blueshell.api.event.api

import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.event.persistence.PingedRole
import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.time.Instant

class EventPostsTest {
    private val file = Entities.file(path = "events/lan.webp")
    private val event =
        Entities
            .event(
                id = 42,
                approved = true,
                title = "LAN party",
                signUp = true,
                signUpCount = 10,
                startTime = Instant.parse("2026-10-10T18:00:00Z"),
                endTime = Instant.parse("2026-10-10T21:00:00Z"),
            ).also {
                it.signUpLimit = 30
                it.pingedRoles += PingedRole("901", "Gamers")
                it.banner = Entities.banner(it, file)
            }
    private val blobs: BlobStore = mock { on { open("events/lan.webp") } doReturn ByteArrayInputStream(byteArrayOf(1, 2)) }
    private val signUps: EventSignUpRepository = mock { on { findLinkedDiscordIds(42) } doReturn listOf("111", "222") }

    @Test
    fun `reads an event as the bot posts it, with its pinged roles and its banner's public path`() {
        val events: EventRepository = mock { on { findByIdIncludingDeleted(42) } doReturn event }

        val read = EventPosts(events, signUps, blobs).of(42)!!

        assertThat(read.live).isTrue()
        assertThat(read.title).isEqualTo("LAN party")
        assertThat(read.signUp).isTrue()
        assertThat(read.signUpCount to read.signUpLimit).isEqualTo(10L to 30)
        assertThat(read.pingedRoleIds).containsExactly("901")
        assertThat(read.goingDiscordIds).containsExactly("111", "222")
        assertThat(read.bannerPath).isEqualTo("/files/public/events/lan.webp")
    }

    @Test
    fun `reads an event that was deleted, or never approved, as no longer live`() {
        val deleted =
            Entities
                .event(id = 43, approved = true, title = "Gone", startTime = Instant.EPOCH, endTime = Instant.EPOCH)
                .also { it.deletedAt = Instant.EPOCH }
        val events: EventRepository = mock { on { findByIdIncludingDeleted(43) } doReturn deleted }

        assertThat(EventPosts(events, signUps, blobs).of(43)!!.live).isFalse()
        assertThat(EventPosts(events, signUps, blobs).of(44)).isNull()
    }

    @Test
    fun `reads an event awaiting re-approval as not live but frozen, unless it is deleted`() {
        val waiting =
            Entities
                .event(id = 45, title = "Waiting", startTime = Instant.EPOCH, endTime = Instant.EPOCH)
                .also { it.awaitingReapproval = true }
        val events: EventRepository = mock { on { findByIdIncludingDeleted(45) } doReturn waiting }

        val read = EventPosts(events, signUps, blobs).of(45)!!
        assertThat(read.live to read.frozen).isEqualTo(false to true)
        waiting.deletedAt = Instant.EPOCH
        assertThat(EventPosts(events, signUps, blobs).of(45)!!.frozen).isFalse()
    }

    @Test
    fun `hands over the banner's bytes, and the approved events near a window`() {
        val events: EventRepository =
            mock {
                on { findByIdIncludingDeleted(42) } doReturn event
                on { findKeptIdsOverlapping(Instant.EPOCH, Instant.MAX) } doReturn listOf(42L)
            }

        val image = EventPosts(events, signUps, blobs).bannerOf(42)!!

        assertThat(image.mediaType).isEqualTo("image/webp")
        assertThat(image.bytes).containsExactly(1, 2)
        assertThat(EventPosts(events, signUps, blobs).bannerOf(44)).isNull()
        assertThat(EventPosts(events, signUps, blobs).keptOverlapping(Instant.EPOCH, Instant.MAX)).containsExactly(42L)
    }

    @Test
    fun `hands over the widest rendition a Discord event's cover takes, not the master`() {
        val small = Entities.file(path = "events/lan-800.webp", renditionWidth = 800)
        val cover = Entities.file(path = "events/lan-1600.webp", renditionWidth = 1600)
        val huge = Entities.file(renditionWidth = 3200)
        val file = Entities.file(path = "events/lan.webp", renditions = listOf(small, cover, huge))
        val event = Entities.event(id = 42).also { it.banner = Entities.banner(it, file) }
        whenever(blobs.open("events/lan-1600.webp")).thenReturn(ByteArrayInputStream(byteArrayOf(9)))
        val events: EventRepository = mock { on { findByIdIncludingDeleted(42) } doReturn event }

        assertThat(EventPosts(events, signUps, blobs).bannerOf(42)!!.bytes).containsExactly(9)
    }
}
