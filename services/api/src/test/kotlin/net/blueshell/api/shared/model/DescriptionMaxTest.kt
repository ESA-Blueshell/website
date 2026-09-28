package net.blueshell.api.shared.model

import jakarta.validation.Validation
import net.blueshell.api.board.web.AddBoardMemberRequest
import net.blueshell.api.board.web.UpdateBoardMemberRequest
import net.blueshell.api.game.web.CasualGameRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DescriptionMaxTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val day = LocalDate.parse("2026-09-01")
    private val full = "a".repeat(DESCRIPTION_MAX)
    private val over = "a".repeat(DESCRIPTION_MAX + 1)

    private fun refused(request: Any): List<String> = validator.validate(request).map { it.propertyPath.toString() }

    @Test
    fun `a board member's description holds as much as Discord's embed, and leaving it out is fine`() {
        assertThat(refused(AddBoardMemberRequest(role = "Chair", startDate = day))).isEmpty()
        assertThat(refused(AddBoardMemberRequest(role = "Chair", startDate = day, description = full))).isEmpty()
        assertThat(refused(AddBoardMemberRequest(role = "Chair", startDate = day, description = over))).containsExactly("description")
        assertThat(refused(UpdateBoardMemberRequest(role = "Chair", startDate = day))).isEmpty()
        assertThat(refused(UpdateBoardMemberRequest(role = "Chair", startDate = day, description = over))).containsExactly("description")
    }

    @Test
    fun `a game's intro holds as much, and leaving it out is fine`() {
        assertThat(refused(CasualGameRequest(name = "Valorant", slug = "valorant"))).isEmpty()
        assertThat(refused(CasualGameRequest(name = "Valorant", slug = "valorant", intro = full))).isEmpty()
        assertThat(refused(CasualGameRequest(name = "Valorant", slug = "valorant", intro = over))).containsExactly("intro")
    }
}
