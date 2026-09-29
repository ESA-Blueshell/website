package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.shared.model.DESCRIPTION_MAX
import net.blueshell.api.sync.api.DISCORD_TEXT_MAX
import net.blueshell.api.sync.api.DiscordLink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DiscordPostContentTest {
    private val event =
        EventPostData(
            id = 42,
            live = true,
            title = "LAN party",
            description = "Bring your own rig.",
            location = "Pakhuis",
            startTime = Instant.parse("2026-10-10T18:00:00Z"),
            endTime = Instant.parse("2026-10-10T21:00:00Z"),
            memberPrice = 5.0,
            publicPrice = 7.5,
            membersOnly = false,
            signUp = true,
            signUpCount = 10,
            signUpLimit = 30,
            signUpDeadline = Instant.parse("2026-10-08T22:00:00Z"),
            pingedRoleIds = listOf("901", "902"),
            bannerPath = "/files/public/events/lan.webp",
        )

    private val site = "https://esa-blueshell.nl"

    private fun detailsOf(event: EventPostData) =
        DiscordPostContent
            .postOf(event, site)
            .text
            .lines()
            .filter { it.startsWith("**") }

    @Test
    fun `says the title, the description, when, where and for how much, then the roles, and links the event page and its sign-up`() {
        val post = DiscordPostContent.postOf(event, site)

        assertThat(post.pingedRoleIds).containsExactly("901", "902")
        assertThat(post.text).isEqualTo(
            """
            ## LAN party

            Bring your own rig.

            **When:** `10 Oct 2026 - 20:00-23:00`
            **Where:** Pakhuis
            **Price:** €5.00 for members, €7.50 for others
            **Signed up:** 10/30
            **Sign up before:** `9 Oct 2026 - 00:00`

            <@&901> <@&902>
            """.trimIndent(),
        )
        assertThat(post.links).containsExactly(
            DiscordLink("More on the site", "https://esa-blueshell.nl/events/42"),
            DiscordLink("Sign up", "https://esa-blueshell.nl/events/42#signup"),
        )
    }

    @Test
    fun `lists who is going by mention after the details, and counts guests and accounts without Discord`() {
        val post = DiscordPostContent.postOf(event.copy(goingDiscordIds = listOf("111", "222")), site)

        assertThat(post.text)
            .contains("**Sign up before:** `9 Oct 2026 - 00:00`\n\n**Going:** <@111>, <@222> and 8 others\n\n<@&901> <@&902>")
        assertThat(DiscordPostContent.postOf(event.copy(signUpCount = 3, goingDiscordIds = listOf("111", "222")), site).text)
            .contains("**Going:** <@111>, <@222> and 1 other\n")
        assertThat(DiscordPostContent.postOf(event.copy(signUpCount = 2, goingDiscordIds = listOf("111", "222")), site).text)
            .contains("**Going:** <@111>, <@222>\n")
    }

    @Test
    fun `lists nobody where nobody linked is going or nobody signs up on the site`() {
        assertThat(DiscordPostContent.postOf(event, site).text).doesNotContain("Going")
        assertThat(DiscordPostContent.postOf(event.copy(signUp = false, goingDiscordIds = listOf("111")), site).text)
            .doesNotContain("Going")
    }

    @Test
    fun `counts whoever does not fit in the list, so a full event keeps its description`() {
        val ids = List(200) { "8035111022467891${it.toString().padStart(3, '0')}" }
        val text = DiscordPostContent.postOf(event.copy(signUpCount = 250, goingDiscordIds = ids), site).text
        val going = text.lines().single { it.startsWith("**Going:**") }

        assertThat(going.length).isLessThan(1600)
        assertThat(going).endsWith(" and ${250 - Regex("<@\\d+>").findAll(going).count()} others")
        assertThat(text).contains("Bring your own rig.")
    }

    @Test
    fun `leaves out a description and mentions the event does not have`() {
        val post = DiscordPostContent.postOf(event.copy(description = " ", pingedRoleIds = emptyList()), site)

        assertThat(post.text).startsWith("## LAN party\n\n**When:**").endsWith("**Sign up before:** `9 Oct 2026 - 00:00`")
    }

    @Test
    fun `says members only, free, a span of days, and leaves out what the event does not have, sign-up included`() {
        val post =
            DiscordPostContent.postOf(
                event.copy(
                    membersOnly = true,
                    memberPrice = null,
                    publicPrice = 0.0,
                    location = " ",
                    endTime = Instant.parse("2026-10-11T12:00:00Z"),
                    signUp = false,
                    signUpDeadline = null,
                ),
                site,
            )

        assertThat(post.links.map { it.label }).containsExactly("More on the site")
        assertThat(post.text.lines().filter { it.startsWith("**") }).containsExactly(
            "**When:** `10 Oct 2026 - 20:00 to 11 Oct 2026 - 14:00`",
            "**Price:** Free",
            "**Members only:** Yes",
        )
    }

    @Test
    fun `says the whole description where it fits, and cuts one near the cap to what Discord takes, roles kept`() {
        val whole = "w".repeat(3000) + " word"
        val long = "word ".repeat(DESCRIPTION_MAX / 5)

        assertThat(DiscordPostContent.postOf(event.copy(description = whole), site).text).contains("\n\n$whole\n\n")
        val cut = DiscordPostContent.postOf(event.copy(description = long), site).text
        assertThat(cut.length).isLessThanOrEqualTo(DISCORD_TEXT_MAX).isGreaterThan(DISCORD_TEXT_MAX - 10)
        assertThat(cut).contains("word…\n\n**When:**").endsWith("<@&901> <@&902>")
    }

    @Test
    fun `cuts past a limit at a space or a line break, never inside an emoji or a mention`() {
        assertThat(DiscordPostContent.cut("one two\nthree <@123456789012345678>", 20)).isEqualTo("one two\nthree…")
        assertThat(DiscordPostContent.cut("aaaa<:POGGERS:657733730491826186>", 12)).isEqualTo("aaaa…")
        assertThat(DiscordPostContent.cut("a".repeat(30), 10)).isEqualTo("a".repeat(9) + "…")
        assertThat(DiscordPostContent.cut("<a:x:123>bbbbbbbbbbbbbbbbb", 12)).isEqualTo("<a:x:123>bb…")
        assertThat(DiscordPostContent.cut("<abcdefghijklmnop", 10)).isEqualTo("<abcdefgh…")
    }

    @Test
    fun `lists the event in the server with its place, or Discord, and the site and sign-up links in its description`() {
        val listing = DiscordPostContent.listingOf(event, site, cover = "data:image/png;base64,AAAA")

        assertThat(listing.name).isEqualTo("LAN party")
        assertThat(listing.location).isEqualTo("Pakhuis")
        assertThat(listing.start).isEqualTo(event.startTime)
        assertThat(listing.end).isEqualTo(event.endTime)
        assertThat(listing.description)
            .endsWith("More on the site: https://esa-blueshell.nl/events/42\nSign up: https://esa-blueshell.nl/events/42#signup")
        assertThat(DiscordPostContent.listingOf(event.copy(signUp = false), site, cover = null).description)
            .endsWith("More on the site: https://esa-blueshell.nl/events/42")
        assertThat(listing.cover).isEqualTo("data:image/png;base64,AAAA")
        assertThat(DiscordPostContent.listingOf(event.copy(location = null), site, cover = null).location).isEqualTo("Discord")
    }

    @Test
    fun `counts sign-ups against no limit as a bare number, and against a limit of none as full`() {
        val details = detailsOf(event.copy(signUpLimit = null)) + detailsOf(event.copy(signUpLimit = 0, signUpCount = 3))

        assertThat(details.filter { it.startsWith("**Signed up:**") }).containsExactly("**Signed up:** 10", "**Signed up:** 3/0")
    }

    @Test
    fun `keeps a Discord event's description within Discord's 1000 characters, links and all`() {
        val listing = DiscordPostContent.listingOf(event.copy(description = "word ".repeat(400).trim()), site, cover = null)

        assertThat(listing.description.length).isLessThanOrEqualTo(1000)
        assertThat(listing.description).contains("word…").endsWith("#signup")
    }

    @Test
    fun `leaves out a sign-up deadline that falls at the start`() {
        assertThat(DiscordPostContent.postOf(event.copy(signUpDeadline = event.startTime), site).text).doesNotContain("Sign up before")
    }

    @Test
    fun `writes September as Sept`() {
        val details =
            detailsOf(event.copy(startTime = Instant.parse("2026-09-24T17:00:00Z"), endTime = Instant.parse("2026-09-24T18:00:00Z")))

        assertThat(details.first()).isEqualTo("**When:** `24 Sept 2026 - 19:00-20:00`")
    }
}
