package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventBannerImage
import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.DiscordPostTrigger
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.time.LocalDateTime

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
            signUpDeadline = null,
            pingedRoleIds = listOf("901"),
            bannerPath = "/files/public/events/lan.webp",
        )

    /** Remembers every call, and fails the ones a test says Discord refuses. */
    private class RecordingPublisher : DiscordPublisher {
        val said = mutableListOf<String>()
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
            return id().also { said += "post $channel $it" }
        }

        override fun edit(
            channel: String,
            messageId: String,
            post: DiscordPost,
        ) {
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
        return DiscordEventPosts(events, provider, ledger, "https://esa-blueshell.nl", "events-info", "events-calendar")
    }

    @Test
    fun `announces the event and lists it in the server two weeks ahead, once`() {
        val posts = posts()

        posts.reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00"))
        posts.reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-27T08:00"))

        assertThat(publisher.said).containsExactly("post events-info m1", "list m2")
        assertThat(publisher.listings.single().cover).isEqualTo("data:image/webp;base64,AQID")
        assertThat(ledger.posted.keys).containsExactlyInAnyOrder(DiscordArtefact.INFO_POST, DiscordArtefact.DISCORD_EVENT)
    }

    @Test
    fun `posts nothing before its time, for a claim another run holds, or without a bot`() {
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-25T08:00"))
        ledger.othersHoldClaims = true
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00"))
        ledger.othersHoldClaims = false
        posts(bot = null).reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00"))

        assertThat(publisher.said).isEmpty()
    }

    @Test
    fun `gives the claim back when Discord refuses, so a retry can post`() {
        publisher.refuse = true

        assertThatThrownBy { posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00")) }.hasMessageContaining("refused")

        assertThat(ledger.claimed).isEmpty()
        publisher.refuse = false
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T09:30"))
        assertThat(publisher.said).containsExactly("post events-info m1", "list m2")
    }

    @Test
    fun `puts the day post up on the day and takes it down the morning after, keeping the events-info post`() {
        val posts = posts()
        posts.reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00"))

        posts.reconcile(42, DiscordPostTrigger.MORNING, at("2026-10-10T08:00"))
        posts.reconcile(42, DiscordPostTrigger.MORNING, at("2026-10-11T08:00"))

        assertThat(publisher.said).containsExactly(
            "post events-info m1",
            "list m2",
            "post events-calendar m3",
            "delete events-calendar m3",
            "unlist m2",
        )
        assertThat(ledger.posted.keys).containsExactly(DiscordArtefact.INFO_POST)
    }

    @Test
    fun `edits what is out when the event changes, and only then`() {
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-10-10T08:00"))
        assertThat(publisher.said).containsExactly("post events-info m1", "post events-calendar m2", "list m3")
        publisher.said.clear()

        posts().reconcile(42, DiscordPostTrigger.CHANGE, at("2026-10-10T09:00"))
        assertThat(publisher.said).isEmpty()

        posts(found = event.copy(title = "LAN party, moved upstairs")).reconcile(42, DiscordPostTrigger.CHANGE, at("2026-10-10T09:00"))
        assertThat(publisher.said).containsExactly("edit events-info m1", "edit events-calendar m2", "relist m3")
    }

    @Test
    fun `takes a day post down when the event moves off the day, and keeps the rest`() {
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-10-10T08:00"))
        publisher.said.clear()
        val moved = event.copy(startTime = at("2026-12-10T20:00"), endTime = at("2026-12-10T23:00"))

        posts(found = moved).reconcile(42, DiscordPostTrigger.CHANGE, at("2026-10-10T09:00"))

        assertThat(publisher.said).containsExactly("edit events-info m1", "delete events-calendar m2", "relist m3")
    }

    @Test
    fun `removes everything when the event is deleted or no longer approved`() {
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-10-10T08:00"))
        publisher.said.clear()

        posts(found = event.copy(live = false)).reconcile(42, DiscordPostTrigger.CHANGE, at("2026-10-10T09:00"))

        assertThat(publisher.said).containsExactly("delete events-info m1", "delete events-calendar m2", "unlist m3")
        assertThat(ledger.posted).isEmpty()
    }

    @Test
    fun `removes everything for an event that is gone altogether`() {
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00"))
        publisher.said.clear()

        posts(found = null).reconcile(42, DiscordPostTrigger.CHANGE, at("2026-09-27T09:00"))

        assertThat(publisher.said).containsExactly("delete events-info m1", "unlist m2")
    }

    @Test
    fun `lists an event with no banner without a cover`() {
        posts(banner = null).reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00"))

        assertThat(publisher.listings.single().cover).isNull()
    }

    @Test
    fun `lists the Discord event on a later run where listing it failed beside the events-info post`() {
        publisher.refuseDiscordEvents = true
        assertThatThrownBy { posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:00")) }.hasMessageContaining("refused")

        publisher.refuseDiscordEvents = false
        posts().reconcile(42, DiscordPostTrigger.MORNING, at("2026-09-26T08:02"))

        assertThat(publisher.said).containsExactly("post events-info m1", "list m2")
    }
}

