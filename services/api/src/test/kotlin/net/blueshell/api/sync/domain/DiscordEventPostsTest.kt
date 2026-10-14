package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventBannerImage
import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.sync.api.DiscordEventListing
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.api.testsupport.configuredDiscordSettings
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
            // The board chose the morning of Saturday 26 September when it approved the event.
            announceAt = at("2026-09-26T08:00"),
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
            reference: String,
            post: DiscordPost,
        ): Boolean {
            if (reference in gone) return false
            banners += post.banner?.fileName
            said += "edit $channel $reference"
            return true
        }

        override fun delete(
            channel: String,
            reference: String,
        ) {
            said += "delete $channel $reference"
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

        /** Discord events Discord has ended: still there when asked after, but refusing an edit. */
        val finished = mutableSetOf<String>()

        override fun updateDiscordEvent(
            discordEventId: String,
            listing: DiscordEventListing,
        ): Boolean {
            if (discordEventId in gone || discordEventId in finished) return false
            listings += listing
            said += "relist $discordEventId"
            return true
        }

        override fun deleteDiscordEvent(discordEventId: String) {
            said += "unlist $discordEventId"
        }

        override fun linkOf(
            channel: String,
            reference: String,
        ) = "https://discord.test/$channel/$reference"

        override fun linkOfDiscordEvent(discordEventId: String) = "https://discord.test/events/$discordEventId"

        /** Gone by the time an edit reaches it, though still there when asked after. */
        val vanishing = mutableSetOf<String>()

        /** What a run asked Discord after by reference, and the reference Discord answers for an old one. */
        val checked = mutableListOf<String>()
        val upgraded = mutableMapOf<String, String>()

        override fun stillPosted(
            channel: String,
            reference: String,
        ): String? {
            checked += reference
            return if (reference in gone && reference !in vanishing) null else upgraded[reference] ?: reference
        }

        override fun stillListed(discordEventId: String): Boolean {
            checked += discordEventId
            return discordEventId !in gone
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
        return DiscordEventPosts(events, provider, ledger, "https://esa-blueshell.nl", configuredDiscordSettings()).apply {
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
    fun `announces the event at the time the board chose, with its banner attached, once`() {
        assertThat(posts("2026-09-26T08:00").keepAnnouncement(42).effect).isEqualTo(JobEffect.MADE)
        assertThat(posts("2026-09-27T08:00").keepAnnouncement(42).effect).isNotEqualTo(JobEffect.MADE)

        assertThat(publisher.said).containsExactly("post events-info m1")
        assertThat(publisher.banners).containsExactly("banner.webp")
        assertThat(ledger.posted.keys).containsExactly(DiscordArtefact.INFO_POST)
    }

    @Test
    fun `posts nothing before its time or without a bot, and retries later for a claim another run holds`() {
        assertThat(posts("2026-09-25T08:00").keepAnnouncement(42).effect).isNotEqualTo(JobEffect.MADE)
        ledger.othersHoldClaims = true
        assertThatThrownBy { posts("2026-09-26T08:00").keepAnnouncement(42) }.hasMessageContaining("Another run")
        ledger.othersHoldClaims = false
        assertThat(posts("2026-09-26T08:00", bot = null).keepAnnouncement(42).effect).isNotEqualTo(JobEffect.MADE)
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
    fun `lists the Discord event once approved, however far ahead, with the banner as its cover`() {
        assertThat(posts("2026-06-01T09:00").keepAnnouncement(42).effect).isNull()
        posts("2026-06-01T09:00").keepDiscordEvent(42)
        posts("2026-09-27T08:00").keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("list m1")
        assertThat(publisher.listings.single().cover).isEqualTo("data:image/webp;base64,AQID")
        assertThat(publisher.listings.single().start).isEqualTo(event.startTime)
    }

    @Test
    fun `puts the day post up on the day and takes it down the morning after, keeping the events-info post and the Discord event`() {
        all("2026-09-26T08:00")
        all("2026-10-10T08:00")
        all("2026-10-10T23:30")
        all("2026-10-11T08:00")

        assertThat(publisher.said).containsExactly(
            "post events-info m1",
            "list m2",
            "post events-calendar m3",
            "delete events-calendar m3",
        )
        assertThat(ledger.posted.keys).containsExactlyInAnyOrder(DiscordArtefact.INFO_POST, DiscordArtefact.DISCORD_EVENT)
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

        posts("2026-09-25T08:00").keepCalendarPost(42)
        posts("2026-09-25T08:00", found = event.copy(live = false)).keepDiscordEvent(42)

        assertThat(publisher.said).containsExactly("delete events-calendar c1", "unlist e1")
        assertThat(ledger.posted).isEmpty()
    }

    @Test
    fun `puts a post somebody removed by hand back on the next run, though nothing changed, notifying again`() {
        posts("2026-09-26T08:00").keepAnnouncement(42)
        publisher.gone += "m1"

        assertThat(posts("2026-09-27T08:00").keepAnnouncement(42).effect).isEqualTo(JobEffect.MADE)

        assertThat(publisher.said).containsExactly("post events-info m1", "post events-info m2")
        assertThat(ledger.posted[DiscordArtefact.INFO_POST]?.externalId).isEqualTo("m2")
    }

    @Test
    fun `makes a post again that goes between being asked after and being edited`() {
        posts("2026-09-26T08:00").keepAnnouncement(42)
        publisher.gone += "m1"
        publisher.vanishing += "m1"

        assertThat(posts("2026-09-27T08:00", found = event.copy(title = "LAN party, bigger")).keepAnnouncement(42).effect)
            .isEqualTo(JobEffect.MADE)

        assertThat(publisher.said).containsExactly("post events-info m1", "post events-info m2")
    }

    @Test
    fun `lists a Discord event somebody removed by hand again on the next run`() {
        posts("2026-09-26T08:00").keepDiscordEvent(42)
        publisher.gone += "m1"

        assertThat(posts("2026-09-27T08:00").keepDiscordEvent(42).effect).isEqualTo(JobEffect.MADE)

        assertThat(publisher.said).containsExactly("list m1", "list m2")
        assertThat(ledger.posted[DiscordArtefact.DISCORD_EVENT]?.externalId).isEqualTo("m2")
    }

    @Test
    fun `asks Discord after what it recorded on every run, whether or not anything changed`() {
        all("2026-10-10T08:00")
        publisher.checked.clear()

        all("2026-10-10T09:00")

        assertThat(publisher.checked).containsExactly("m1", "m2", "m3")
        assertThat(publisher.said).containsExactly("post events-info m1", "post events-calendar m2", "list m3")
    }

    @Test
    fun `records the reference Discord answers for a post recorded the old way, and keeps that copy`() {
        posts("2026-09-26T08:00").keepAnnouncement(42)
        val old = ledger.posted.getValue(DiscordArtefact.INFO_POST)
        ledger.posted[DiscordArtefact.INFO_POST] = old.copy(externalId = "legacy")
        publisher.upgraded["legacy"] = "111/legacy"
        publisher.strays["events-info"] = listOf("111/legacy")

        val kept = posts("2026-09-27T08:00").keepAnnouncement(42)

        assertThat(kept).isEqualTo(Kept(JobEffect.UNCHANGED, "https://discord.test/events-info/111/legacy"))
        assertThat(ledger.posted[DiscordArtefact.INFO_POST]).isEqualTo(old.copy(externalId = "111/legacy"))
        assertThat(publisher.said).containsExactly("post events-info m1")
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
            assertThat(keepAnnouncement(42).effect).isEqualTo(JobEffect.MADE)
            assertThat(keepCalendarPost(42).effect).isEqualTo(JobEffect.MADE)
            assertThat(keepDiscordEvent(42).effect).isEqualTo(JobEffect.MADE)
        }

        assertThat(publisher.said).containsExactly("post events-info m1", "post events-calendar m2", "list m3")
        assertThat(publisher.listings.single().start).isEqualTo(at("2026-10-11T09:01"))
    }

    @Test
    fun `freezes what is out while the event awaits re-approval, and brings it up to date once approved again`() {
        all("2026-10-10T08:00")
        publisher.said.clear()
        val waiting = event.copy(live = false, frozen = true, title = "LAN party, typo fixed")

        val kept =
            posts(
                "2026-10-10T09:00",
                found = waiting,
            ).run { listOf(keepAnnouncement(42), keepCalendarPost(42), keepDiscordEvent(42)) }
        assertThat(kept.map { it.skipped }.distinct())
            .containsExactly("The event awaits re-approval, so what is out stays as last approved.")
        assertThat(publisher.said).isEmpty()

        all("2026-10-10T10:00", found = waiting.copy(live = true, frozen = false))
        assertThat(publisher.said).containsExactly("edit events-info m1", "edit events-calendar m2", "relist m3")
    }

    @Test
    fun `still takes down on time what is frozen, and makes nothing new for it`() {
        all("2026-10-10T08:00")
        publisher.said.clear()
        val waiting = event.copy(live = false, frozen = true)

        all("2026-10-10T23:30", found = waiting)
        all("2026-10-11T08:00", found = waiting)
        assertThat(publisher.said).containsExactly("delete events-calendar m2")
        assertThat(ledger.posted.keys).containsExactlyInAnyOrder(DiscordArtefact.INFO_POST, DiscordArtefact.DISCORD_EVENT)

        ledger.posted.clear()
        publisher.said.clear()
        all("2026-10-01T08:00", found = waiting)
        assertThat(publisher.said).isEmpty()
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
        assertThat(posts("2026-10-11T08:00").keepDiscordEvent(42).skipped)
            .isEqualTo("The event is over, and Discord ends its Discord event by itself.")
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

        val kept = posts("2026-09-25T08:00", found = event.copy(live = false)).keepDiscordEvent(42)

        assertThat(kept).isEqualTo(Kept(JobEffect.REMOVED))
        assertThat(publisher.said).containsExactly("unlist e1")
    }

    @Test
    fun `a forced run does what it would wait for`() {
        posts("2026-09-20T09:00").run {
            assertThat(keepAnnouncement(42, forced = true).effect).isEqualTo(JobEffect.MADE)
            assertThat(keepCalendarPost(42, forced = true).effect).isEqualTo(JobEffect.MADE)
        }

        assertThat(publisher.said).containsExactly("post events-info m1", "post events-calendar m2")
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
        assertThat(posts("2026-10-11T08:00").keepCalendarPost(42, forced = true).skipped)
            .isEqualTo("The event's day is over, so its #events-calendar post has come down.")
        assertThat(publisher.said).isEmpty()
    }

    @Test
    fun `leaves the Discord event to Discord once the event is over, whatever happens to the event then`() {
        posts("2026-10-01T09:00").keepDiscordEvent(42)
        publisher.checked.clear()

        val kept = posts("2026-10-11T08:00", found = event.copy(live = false, title = "LAN party, renamed")).keepDiscordEvent(42)

        assertThat(kept.skipped).isEqualTo("The event is over, and Discord ends its Discord event by itself.")
        assertThat(publisher.said).containsExactly("list m1")
        assertThat(publisher.checked).isEmpty()
        assertThat(ledger.posted.keys).containsExactly(DiscordArtefact.DISCORD_EVENT)
    }

    @Test
    fun `lists a new Discord event for an event moved ahead again after Discord ended the old one`() {
        posts("2026-10-01T09:00").keepDiscordEvent(42)
        publisher.finished += "m1"
        val moved = event.copy(startTime = at("2026-12-10T20:00"), endTime = at("2026-12-10T23:00"))

        assertThat(posts("2026-10-12T09:00", found = moved).keepDiscordEvent(42).effect).isEqualTo(JobEffect.MADE)

        assertThat(publisher.said).containsExactly("list m1", "list m2")
        assertThat(ledger.posted[DiscordArtefact.DISCORD_EVENT]?.externalId).isEqualTo("m2")
    }

    @Test
    fun `lists no Discord event for an event ending within a minute`() {
        assertThat(posts("2026-10-10T22:59:30").keepDiscordEvent(42).skipped)
            .isEqualTo("The event ends within a minute, too soon for Discord to list it.")
        assertThat(publisher.said).isEmpty()
    }
}
