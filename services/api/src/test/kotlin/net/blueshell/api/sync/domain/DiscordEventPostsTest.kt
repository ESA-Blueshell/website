package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventBannerImage
import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class DiscordEventPostsTest {
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(DiscordPostSchedule.ZONE).toInstant()

    private val event =
        EventPostData(
            id = 42,
            live = true,
            title = "LAN party",
            description = "Bring your own rig.",
            location = "Pakhuis",
            startTime = at("2026-10-10T20:00"),
            endTime = at("2026-10-10T23:00"),
            memberPrice = null,
            publicPrice = null,
            membersOnly = false,
            signUp = false,
            signUpDeadline = null,
            pingedRoleIds = listOf("901"),
            bannerPath = "/files/public/events/lan.webp",
        )

    /** Remembers every call, and fails the ones a test says Discord refuses. */
    private class RecordingPublisher : DiscordPublisher {
        val said = mutableListOf<String>()
        val banners = mutableListOf<String?>()
        val listings = mutableListOf<DiscordEventListing>()
        var refuseDiscordEvents = false
        var refuse = false
        private var next = 0

        private fun id() = "m${++next}"

        override fun post(
            channel: String,
            post: DiscordPost,
        ): String {
            if (refuse) error("Discord refused")
            banners += post.banner?.fileName
            return id().also { said += "post $channel $it" }
        }

        override fun edit(
            channel: String,
            messageId: String,
            post: DiscordPost,
        ) {
            banners += post.banner?.fileName
            said += "edit $channel $messageId"
        }

        override fun delete(
            channel: String,
            messageId: String,
        ) {
            said += "delete $channel $messageId"
        }

        override fun createDiscordEvent(listing: DiscordEventListing): String {
            if (refuseDiscordEvents) error("Discord refused the Discord event")
            return id().also {
                listings += listing
                said += "list $it"
            }
        }

        override fun updateDiscordEvent(
            discordEventId: String,
            listing: DiscordEventListing,
        ) {
            listings += listing
            said += "relist $discordEventId"
        }

        override fun deleteDiscordEvent(discordEventId: String) {
            said += "unlist $discordEventId"
        }
    }

    /** The ledger as a map, with a switch for a claim somebody else holds. */
    private class MemoryLedger : PostLedger {
        val posted = mutableMapOf<DiscordArtefact, RecordedArtefact>()
        val claimed = mutableSetOf<DiscordArtefact>()
        var othersHoldClaims = false

        override fun find(
            eventId: Long,
            artefact: DiscordArtefact,
        ) = posted[artefact]

        override fun claim(
            eventId: Long,
            artefact: DiscordArtefact,
            now: Instant,
        ): Boolean = !othersHoldClaims && claimed.add(artefact)

        override fun record(
            eventId: Long,
            artefact: DiscordArtefact,
            externalId: String,
            fingerprint: Long,
        ) {
            posted[artefact] = RecordedArtefact(externalId, fingerprint)
        }

        override fun release(
            eventId: Long,
            artefact: DiscordArtefact,
        ) {
            posted.remove(artefact)
            claimed.remove(artefact)
        }
    }

    private val publisher = RecordingPublisher()
    private val ledger = MemoryLedger()

    private fun posts(
        now: String,
        found: EventPostData? = event,
        bot: DiscordPublisher? = publisher,
        banner: EventBannerImage? = EventBannerImage("image/webp", byteArrayOf(1, 2, 3)),
    ): DiscordEventPosts {
        val events: EventPosts =
            mock {
                on { of(42) } doReturn found
                on { bannerOf(42) } doReturn banner
            }
        val provider: ObjectProvider<DiscordPublisher> = mock { on { ifAvailable } doReturn bot }
        return DiscordEventPosts(events, provider, ledger, "https://esa-blueshell.nl", "events-info", "events-calendar").apply {
            clock = Clock.fixed(at(now), ZoneOffset.UTC)
        }
    }

    private fun all(
        now: String,
        found: EventPostData? = event,
    ) = posts(now, found).run {
        keepAnnouncement(42)
        keepCalendarPost(42)
        keepDiscordEvent(42)
    }

    @Test
    fun `announces the event two weeks ahead with its banner attached, once`() {
        assertThat(posts("2026-09-26T08:00").keepAnnouncement(42)).isTrue()
        assertThat(posts("2026-09-27T08:00").keepAnnouncement(42)).isFalse()

        assertThat(publisher.said).containsExactly("post events-info m1")
        assertThat(publisher.banners).containsExactly("banner.webp")
        assertThat(ledger.posted.keys).containsExactly(DiscordArtefact.INFO_POST)
    }

    @Test
    fun `posts nothing before its time, for a claim another run holds, or without a bot`() {
        assertThat(posts("2026-09-25T08:00").keepAnnouncement(42)).isFalse()
        ledger.othersHoldClaims = true
        assertThat(posts("2026-09-26T08:00").keepAnnouncement(42)).isFalse()
        ledger.othersHoldClaims = false
        assertThat(posts("2026-09-26T08:00", bot = null).keepAnnouncement(42)).isFalse()
        posts("2026-10-10T08:00", bot = null).keepCalendarPost(42)
        posts("2026-09-26T08:00", bot = null).keepDiscordEvent(42)

        assertThat(publisher.said).isEmpty()
    }

    @Test
    fun `gives the claim back when Discord refuses, so a retry can post`() {
        publisher.refuse = true

        assertThatThrownBy { posts("2026-09-26T08:00").keepAnnouncement(42) }.hasMessageContaining("refused")

        assertThat(ledger.claimed).isEmpty()
        publisher.refuse = false
        posts("2026-09-26T09:30").keepAnnouncement(42)
        assertThat(publisher.said).containsExactly("post events-info m1")
    }

    @Test
    fun `lists the Discord event only beside the events-info post, with the banner as its cover`() {
        posts("2026-09-26T08:00").keepDiscordEvent(42)
        assertThat(publisher.said).isEmpty()

        posts("2026-09-26T08:00").keepAnnouncement(42)
        posts("2026-09-26T08:00").keepDiscordEvent(42)
        posts("2026-09-27T08:00").keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("post events-info m1", "list m2")
        assertThat(publisher.listings.single().cover).isEqualTo("data:image/webp;base64,AQID")
    }

    @Test
    fun `puts the day post up on the day and takes it down the morning after, keeping the events-info post`() {
        all("2026-09-26T08:00")
        all("2026-10-10T08:00")
        all("2026-10-10T23:30")
        all("2026-10-11T08:00")

        assertThat(publisher.said).containsExactly(
            "post events-info m1",
            "list m2",
            "post events-calendar m3",
            "unlist m2",
            "delete events-calendar m3",
        )
        assertThat(ledger.posted.keys).containsExactly(DiscordArtefact.INFO_POST)
    }

    @Test
    fun `edits what is out when the event changes, and only then`() {
        all("2026-10-10T08:00")
        assertThat(publisher.said).containsExactly("post events-info m1", "post events-calendar m2", "list m3")
        publisher.said.clear()

        all("2026-10-10T09:00")
        assertThat(publisher.said).isEmpty()

        all("2026-10-10T09:00", event.copy(title = "LAN party, moved upstairs"))
        assertThat(publisher.said).containsExactly("edit events-info m1", "edit events-calendar m2", "relist m3")
    }

    @Test
    fun `attaches a banner added after the post went out`() {
        posts("2026-09-26T08:00", found = event.copy(bannerPath = null), banner = null).keepAnnouncement(42)

        posts("2026-09-27T10:00").keepAnnouncement(42)

        assertThat(publisher.said).containsExactly("post events-info m1", "edit events-info m1")
        assertThat(publisher.banners).containsExactly(null, "banner.webp")
    }

    @Test
    fun `takes a day post down when the event moves off the day, and keeps the rest`() {
        all("2026-10-10T08:00")
        publisher.said.clear()

        all("2026-10-10T09:00", event.copy(startTime = at("2026-12-10T20:00"), endTime = at("2026-12-10T23:00")))

        assertThat(publisher.said).containsExactly("edit events-info m1", "delete events-calendar m2", "relist m3")
    }

    @Test
    fun `removes everything when the event is deleted or no longer approved`() {
        all("2026-10-10T08:00")
        publisher.said.clear()

        all("2026-10-10T09:00", event.copy(live = false))

        assertThat(publisher.said).containsExactly("delete events-info m1", "delete events-calendar m2", "unlist m3")
        assertThat(ledger.posted).isEmpty()
    }

    @Test
    fun `removes everything for an event that is gone altogether`() {
        all("2026-09-26T08:00")
        publisher.said.clear()

        all("2026-09-27T09:00", found = null)

        assertThat(publisher.said).containsExactly("delete events-info m1", "unlist m2")
    }

    @Test
    fun `lists an event with no banner without a cover`() {
        posts("2026-09-26T08:00", banner = null).run {
            keepAnnouncement(42)
            keepDiscordEvent(42)
        }

        assertThat(publisher.listings.single().cover).isNull()
        assertThat(publisher.banners).containsExactly(null)
    }

    @Test
    fun `lists the Discord event on a later run where listing it failed beside the events-info post`() {
        publisher.refuseDiscordEvents = true
        posts("2026-09-26T08:00").keepAnnouncement(42)
        assertThatThrownBy { posts("2026-09-26T08:00").keepDiscordEvent(42) }.hasMessageContaining("refused")

        publisher.refuseDiscordEvents = false
        posts("2026-09-26T08:02").keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("post events-info m1", "list m2")
    }
}
