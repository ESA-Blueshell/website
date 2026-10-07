package net.blueshell.api.committee.web

import net.blueshell.api.committee.persistence.Committee
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The shapes the committee routes take and answer with, read field by field. */
class CommitteeShapesTest {
    @Test
    fun `a committee answers its address, Archived, banner and games`() {
        val lan =
            Committee(name = "LanCie", description = "LANs", archived = true).apply {
                id = 1
                gameCodes += "CS2"
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }

        val summary = lan.asResponse(withMembers = false)
        val seat = CommitteeSeatResponse(name = "nelly", avatar = "https://cdn/n.png", discord = true, role = "Chair")

        assertThat(listOf(summary.slug, summary.archived, summary.banner, summary.gameCodes))
            .containsExactly("lancie", true, null, listOf("CS2"))
        assertThat(listOf(seat.name, seat.avatar, seat.discord, seat.role)).containsExactly("nelly", "https://cdn/n.png", true, "Chair")
        assertThat(listOf(summary.icon, summary.members, lan.asPageResponse(emptyList()).icon)).containsOnlyNulls()
    }
}
