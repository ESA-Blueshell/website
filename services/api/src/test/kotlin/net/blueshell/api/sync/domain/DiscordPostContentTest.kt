package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
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
            signUpDeadline = Instant.parse("2026-10-08T22:00:00Z"),
            pingedRoleIds = listOf("901", "902"),
            bannerPath = "/files/public/events/lan.webp",
        )

    private val site = "https://esa-blueshell.nl"

    @Test
    fun `says what, when, where and for how much, links the event page and shows its banner`() {
        val post = DiscordPostContent.postOf(event, site)

        assertThat(post.pingedRoleIds).containsExactly("901", "902")
        assertThat(post.embed.title).isEqualTo("LAN party")
        assertThat(post.embed.url).isEqualTo("https://esa-blueshell.nl/events/42")
        assertThat(post.embed.imageUrl).isEqualTo("https://esa-blueshell.nl/api/files/public/events/lan.webp")
        assertThat(post.embed.description).startsWith("Bring your own rig.").contains("[More on the site](https://esa-blueshell.nl/events/42)")
        assertThat(post.embed.fields).containsExactly(
            "When" to "<t:1791655200:F> until <t:1791666000:t>",
            "Where" to "Pakhuis",
            "Price" to "€5.00 for members, €7.50 for others",
            "Sign up before" to "<t:1791496800:F>",
        )
    }

    @Test
    fun `says members only, free, a span of days, and leaves out what the event does not have`() {
        val post =
            DiscordPostContent.postOf(
                event.copy(
                    membersOnly = true,
                    memberPrice = null,
                    publicPrice = 0.0,
                    location = " ",
                    endTime = Instant.parse("2026-10-11T12:00:00Z"),
                    signUpDeadline = null,
                    bannerPath = null,
                ),
                site,
            )

        assertThat(post.embed.imageUrl).isNull()
        assertThat(post.embed.fields).containsExactly(
            "When" to "<t:1791655200:F> until <t:1791720000:F>",
            "Price" to "Free",
            "Members only" to "Yes",
        )
    }

    @Test
    fun `cuts a long description at a word, near 300 characters`() {
        val long = "word ".repeat(100).trim()

        val said = DiscordPostContent.postOf(event.copy(description = long), site).embed.description

        assertThat(said.substringBefore("\n\n")).endsWith("word…").hasSizeLessThanOrEqualTo(301)
    }

    @Test
    fun `lists the event in the server with its place, or online, and the site link in its description`() {
        val listing = DiscordPostContent.listingOf(event, site, cover = "data:image/png;base64,AAAA")

        assertThat(listing.name).isEqualTo("LAN party")
        assertThat(listing.location).isEqualTo("Pakhuis")
        assertThat(listing.start).isEqualTo(event.startTime)
        assertThat(listing.end).isEqualTo(event.endTime)
        assertThat(listing.description).contains("https://esa-blueshell.nl/events/42")
        assertThat(listing.cover).isEqualTo("data:image/png;base64,AAAA")
        assertThat(DiscordPostContent.listingOf(event.copy(location = null), site, cover = null).location).isEqualTo("Online")
    }
}
