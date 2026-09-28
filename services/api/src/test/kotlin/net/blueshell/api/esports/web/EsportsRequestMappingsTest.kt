package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.DraftEntry
import net.blueshell.api.esports.api.LineupDraft
import net.blueshell.api.esports.api.RosterEntryInput
import net.blueshell.api.esports.domain.SeasonInput
import net.blueshell.api.esports.domain.TeamInput
import net.blueshell.api.shared.enums.TeamRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Each request maps to its input in one expression, and nothing is lost on the way. */
class EsportsRequestMappingsTest {
    private val start = LocalDate.of(2030, 9, 1)
    private val end = LocalDate.of(2031, 1, 31)
    private val entry = RosterEntryInput("nova", TeamRole.COACH, "Sanne", "Captain", "Calls it", "icons/a.webp")

    @Test
    fun `a season and a team map to their inputs`() {
        assertThat(SeasonRequest("Autumn", start, end).asInput()).isEqualTo(SeasonInput("Autumn", start, end))
        assertThat(TeamRequest("BS Nomads", "icons/t.webp").asInput()).isEqualTo(TeamInput("BS Nomads", "icons/t.webp"))
    }

    @Test
    fun `an added and an edited roster entry map to one input`() {
        val added = AddRosterEntryRequest("CS2", 5, "nova", TeamRole.COACH, 9, "Sanne", "Captain", "Calls it", "icons/a.webp")
        val edited = UpdateRosterEntryRequest("nova", TeamRole.COACH, "Sanne", "Captain", "Calls it", 2, "icons/a.webp")

        assertThat(added.asInput()).isEqualTo(entry)
        assertThat(edited.asInput()).isEqualTo(entry)
    }

    @Test
    fun `a line-up maps to its draft, the season from the path`() {
        val request =
            PublishLineupRequest(
                teamId = 3,
                name = "BS Nomads",
                game = "CS2",
                removed = listOf(12),
                entries = listOf(LineupEntryRequest(11, "nova", TeamRole.COACH, 9, "Sanne", "Captain", "Calls it", "icons/a.webp")),
            )

        assertThat(request.asDraft(5))
            .isEqualTo(LineupDraft(3, "BS Nomads", null, "CS2", 5, null, listOf(12), listOf(DraftEntry(11, entry, 9))))
    }
}
