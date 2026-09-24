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
            signUp = true,
            signUpCount = 10,
            signUpLimit = 30,
            signUpDeadline = Instant.parse("2026-10-08T22:00:00Z"),
            pingedRoleIds = listOf("901", "902"),
            bannerPath = "/files/public/events/lan.webp",
        )

    private val site = "https://esa-blueshell.nl"

    @Test
    fun `says what, when, where and for how much, and links the event page and its sign-up`() {
        val post = DiscordPostContent.postOf(event, site)

        assertThat(post.pingedRoleIds).containsExactly("901", "902")
        assertThat(post.embed.title).isEqualTo("LAN party")
        assertThat(post.embed.url).isEqualTo("https://esa-blueshell.nl/events/42")
        assertThat(post.embed.description)
            .startsWith("Bring your own rig.")
            .endsWith("[More on the site](https://esa-blueshell.nl/events/42) · [Sign up](https://esa-blueshell.nl/events/42#signup)")
        assertThat(post.embed.fields).containsExactly(
            "When" to "<t:1791655200:F> until <t:1791666000:t>",
            "Where" to "Pakhuis",
            "Price" to "€5.00 for members, €7.50 for others",
            "Signed up" to "10/30",
            "Sign up before" to "<t:1791496800:F>",
        )
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

        assertThat(post.embed.description).endsWith("[More on the site](https://esa-blueshell.nl/events/42)")
        assertThat(post.embed.fields.map { it.first }).doesNotContain("Signed up")
        assertThat(post.embed.fields).containsExactly(
            "When" to "<t:1791655200:F> until <t:1791720000:F>",
            "Price" to "Free",
            "Members only" to "Yes",
        )
    }

    @Test
    fun `says the whole description, cutting at a word only past what Discord holds`() {
        val long = "word ".repeat(700).trim()
        val tooLong = "word ".repeat(800).trim()

        val whole = DiscordPostContent.postOf(event.copy(description = long), site).embed.description
        val cut = DiscordPostContent.postOf(event.copy(description = tooLong), site).embed.description

        assertThat(whole.substringBefore("\n\n")).isEqualTo(long)
        assertThat(cut.substringBefore("\n\n")).endsWith("word…").hasSizeLessThanOrEqualTo(3801)
        assertThat(cut.length).isLessThanOrEqualTo(4096)
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

    @Test
    fun `counts sign-ups against no limit as a bare number`() {
        val fields = DiscordPostContent.postOf(event.copy(signUpLimit = null), site).embed.fields +
            DiscordPostContent.postOf(event.copy(signUpLimit = 0, signUpCount = 3), site).embed.fields

        assertThat(fields.filter { it.first == "Signed up" }.map { it.second }).containsExactly("10", "3")
    }
}
