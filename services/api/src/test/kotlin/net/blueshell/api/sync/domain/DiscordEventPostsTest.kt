package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventBannerImage
import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.JobEffect
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
            signUpCount = 0,
            signUpLimit = null,
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

        /** What somebody removed from the server by hand. */
        val gone = mutableSetOf<String>()

        override fun edit(
            channel: String,
            messageId: String,
            post: DiscordPost,
        ): Boolean {
            if (messageId in gone) return false
            banners += post.banner?.fileName
            said += "edit $channel $messageId"
            return true
        }

        override fun delete(
            channel: String,
            messageId: String,
        ) {
            said += "delete $channel $messageId"
        }

        /** What the server holds that links event 42 without the ledger knowing, by channel or "events". */
        val strays = mutableMapOf<String, List<String>>()

        override fun findPosts(
            channel: String,
            url: String,
        ) = if (url == "https://esa-blueshell.nl/events/42") strays[channel].orEmpty() else emptyList()

        override fun findDiscordEvents(line: String) =
            if (line == "More on the site: https://esa-blueshell.nl/events/42") strays["events"].orEmpty() else emptyList()

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
        ): Boolean {
            if (discordEventId in gone) return false
            listings += listing
            said += "relist $discordEventId"
            return true
        }

        override fun deleteDiscordEvent(discordEventId: String) {
            said += "unlist $discordEventId"
        }

        override fun linkOf(
            channel: String,
            messageId: String,
        ) = "https://discord.test/$channel/$messageId"

        override fun linkOfDiscordEvent(discordEventId: String) = "https://discord.test/events/$discordEventId"
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
        assertThat(posts("2026-09-26T08:00").keepAnnouncement(42).made).isTrue()
        assertThat(posts("2026-09-27T08:00").keepAnnouncement(42).made).isFalse()

        assertThat(publisher.said).containsExactly("post events-info m1")
        assertThat(publisher.banners).containsExactly("banner.webp")
        assertThat(ledger.posted.keys).containsExactly(DiscordArtefact.INFO_POST)
    }

    @Test
    fun `posts nothing before its time or without a bot, and retries later for a claim another run holds`() {
        assertThat(posts("2026-09-25T08:00").keepAnnouncement(42).made).isFalse()
        ledger.othersHoldClaims = true
        assertThatThrownBy { posts("2026-09-26T08:00").keepAnnouncement(42) }.hasMessageContaining("Another run")
        ledger.othersHoldClaims = false
        assertThat(posts("2026-09-26T08:00", bot = null).keepAnnouncement(42).made).isFalse()
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
    fun `lists the Discord event within two weeks without waiting for the events-info post, with the banner as its cover`() {
        posts("2026-09-26T07:59").keepDiscordEvent(42)
        assertThat(publisher.said).isEmpty()

        posts("2026-09-26T08:00").keepDiscordEvent(42)
        posts("2026-09-27T08:00").keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("list m1")
        assertThat(publisher.listings.single().cover).isEqualTo("data:image/webp;base64,AQID")
        assertThat(publisher.listings.single().start).isEqualTo(event.startTime)
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
    fun `keeps what is out when the event moves further away, edited, until the day post's take-down`() {
        all("2026-10-10T08:00")
        publisher.said.clear()

        all("2026-10-10T09:00", event.copy(startTime = at("2026-12-10T20:00"), endTime = at("2026-12-10T23:00")))

        assertThat(publisher.said).containsExactly("edit events-info m1", "edit events-calendar m2", "relist m3")
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
    fun `lists the Discord event on a later run where listing it failed`() {
        publisher.refuseDiscordEvents = true
        posts("2026-09-26T08:00").keepAnnouncement(42)
        assertThatThrownBy { posts("2026-09-26T08:00").keepDiscordEvent(42) }.hasMessageContaining("refused")

        publisher.refuseDiscordEvents = false
        posts("2026-09-26T08:02").keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("post events-info m1", "list m2")
    }

    @Test
    fun `takes over a post Discord already holds rather than posting again, removing any other copy`() {
        publisher.strays["events-info"] = listOf("x1", "x2")

        assertThat(posts("2026-09-26T08:00").keepAnnouncement(42).effect).isEqualTo(JobEffect.EDITED)

        assertThat(publisher.said).containsExactly("edit events-info x1", "delete events-info x2")
        assertThat(ledger.posted[DiscordArtefact.INFO_POST]?.externalId).isEqualTo("x1")
    }

    @Test
    fun `takes over a Discord event already listed, and removes copies beside the recorded one`() {
        posts("2026-09-26T08:00").keepAnnouncement(42)
        publisher.strays["events"] = listOf("e7", "e8")
        posts("2026-09-26T08:00").keepDiscordEvent(42)
        publisher.strays["events"] = listOf("e7", "e9")
        posts("2026-09-26T09:00").keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("post events-info m1", "relist e7", "unlist e8", "unlist e9")
        assertThat(ledger.posted[DiscordArtefact.DISCORD_EVENT]?.externalId).isEqualTo("e7")
    }

    @Test
    fun `removes copies of what should not stand, recorded or not`() {
        publisher.strays["events-calendar"] = listOf("c1")
        publisher.strays["events"] = listOf("e1")

        posts("2026-09-25T08:00").run {
            keepCalendarPost(42)
            keepDiscordEvent(42)
        }

        assertThat(publisher.said).containsExactly("delete events-calendar c1", "unlist e1")
        assertThat(ledger.posted).isEmpty()
    }

    @Test
    fun `makes a post again that somebody removed by hand, once the event changes`() {
        posts("2026-09-26T08:00").keepAnnouncement(42)
        publisher.gone += "m1"

        assertThat(posts("2026-09-27T08:00", found = event.copy(title = "LAN party, bigger")).keepAnnouncement(42).made).isTrue()

        assertThat(publisher.said).containsExactly("post events-info m1", "post events-info m2")
        assertThat(ledger.posted[DiscordArtefact.INFO_POST]?.externalId).isEqualTo("m2")
    }

    @Test
    fun `lists an event already running from a minute on, and leaves the start alone when editing it then`() {
        posts("2026-10-10T20:30").keepDiscordEvent(42)
        posts("2026-10-10T20:40", found = event.copy(title = "LAN party, bigger")).keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("list m1", "relist m1")
        assertThat(publisher.listings.map { it.start }).containsExactly(at("2026-10-10T20:31"), null)
    }

    @Test
    fun `takes over the first copy still there, and makes one where every copy is gone`() {
        publisher.strays["events-info"] = listOf("x1", "x2")
        publisher.gone += "x1"
        posts("2026-09-26T08:00").keepAnnouncement(42)
        assertThat(publisher.said).containsExactly("edit events-info x2", "delete events-info x1")

        ledger.posted.clear()
        ledger.claimed.clear()
        publisher.said.clear()
        publisher.gone += "x2"
        posts("2026-09-26T08:00").keepAnnouncement(42)
        assertThat(publisher.said).containsExactly("post events-info m1", "delete events-info x1", "delete events-info x2")
    }

    @Test
    fun `puts all three up for a multi-day event approved while it runs`() {
        val weekend = event.copy(endTime = at("2026-10-12T16:00"))

        posts("2026-10-11T09:00", found = weekend).run {
            assertThat(keepAnnouncement(42).made).isTrue()
            assertThat(keepCalendarPost(42).made).isTrue()
            assertThat(keepDiscordEvent(42).made).isTrue()
        }

        assertThat(publisher.said).containsExactly("post events-info m1", "post events-calendar m2", "list m3")
        assertThat(publisher.listings.single().start).isEqualTo(at("2026-10-11T09:01"))
    }

    @Test
    fun `says what each run did, with a link to what it did it to`() {
        val made = posts("2026-09-26T08:00").keepAnnouncement(42)
        val unchanged = posts("2026-09-26T09:00").keepAnnouncement(42)
        val edited = posts("2026-09-26T10:00", found = event.copy(title = "LAN party, bigger")).keepAnnouncement(42)
        val listed = posts("2026-09-26T08:00").keepDiscordEvent(42)
        val removed = posts("2026-09-26T11:00", found = null).keepAnnouncement(42)

        assertThat(listOf(made, unchanged, edited, listed, removed)).containsExactly(
            Kept(JobEffect.MADE, "https://discord.test/events-info/m1"),
            Kept(JobEffect.UNCHANGED, "https://discord.test/events-info/m1"),
            Kept(JobEffect.EDITED, "https://discord.test/events-info/m1"),
            Kept(JobEffect.MADE, "https://discord.test/events/m2"),
            Kept(JobEffect.REMOVED),
        )
    }

    @Test
    fun `says why a run did nothing`() {
        assertThat(posts("2026-09-26T08:00", bot = null).keepDiscordEvent(42).skipped).isEqualTo("The Discord bot is not configured.")
        assertThat(posts("2026-09-25T08:00").keepAnnouncement(42).skipped)
            .isEqualTo("The #events-info announcement is not due until 08:00 on 26 September 2026.")
        assertThat(posts("2026-09-25T08:00").keepDiscordEvent(42).skipped)
            .isEqualTo("The Discord event is not due until 08:00 on 26 September 2026.")
        assertThat(posts("2026-09-26T08:00").keepCalendarPost(42).skipped)
            .isEqualTo("The #events-calendar post is not due until 08:00 on 10 October 2026.")
        assertThat(posts("2026-10-11T08:00").keepCalendarPost(42).skipped)
            .isEqualTo("The event's day is over, so its #events-calendar post has come down.")
        assertThat(posts("2026-09-26T08:00", found = null).keepAnnouncement(42).skipped)
            .isEqualTo("The event is deleted or no longer approved.")
        assertThat(publisher.said).isEmpty()
    }

    @Test
    fun `counts taking something down as the run's work, not a skip`() {
        publisher.strays["events"] = listOf("e1")

        val kept = posts("2026-09-25T08:00").keepDiscordEvent(42)

        assertThat(kept).isEqualTo(Kept(JobEffect.REMOVED))
        assertThat(publisher.said).containsExactly("unlist e1")
    }

    @Test
    fun `a forced run does what it would wait for`() {
        posts("2026-09-20T09:00").run {
            assertThat(keepDiscordEvent(42, forced = true).made).isTrue()
            assertThat(keepAnnouncement(42, forced = true).made).isTrue()
            assertThat(keepCalendarPost(42, forced = true).made).isTrue()
        }

        assertThat(publisher.said).containsExactly("list m1", "post events-info m2", "post events-calendar m3")
    }

    @Test
    fun `keeps a forced day post up until its take-down`() {
        posts("2026-09-20T09:00").keepCalendarPost(42, forced = true)

        assertThat(posts("2026-09-21T09:00").keepCalendarPost(42).effect).isEqualTo(JobEffect.UNCHANGED)
        posts("2026-10-11T08:00").keepCalendarPost(42)

        assertThat(publisher.said).containsExactly("post events-calendar m1", "delete events-calendar m1")
    }

    @Test
    fun `a forced run still skips what cannot be done`() {
        assertThat(posts("2026-10-11T08:00").keepAnnouncement(42, forced = true).skipped).isEqualTo("The event is over.")
        assertThat(posts("2026-10-11T08:00").keepDiscordEvent(42, forced = true).skipped).isEqualTo("The event is over.")
        assertThat(posts("2026-10-11T08:00").keepCalendarPost(42, forced = true).skipped)
            .isEqualTo("The event's day is over, so its #events-calendar post has come down.")
        assertThat(posts("2026-10-10T22:59:30").keepDiscordEvent(42, forced = true).skipped)
            .isEqualTo("The event ends within a minute, too soon for Discord to list it.")
        assertThat(publisher.said).isEmpty()
    }
}
