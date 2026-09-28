package net.blueshell.api.board.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class BoardRefusalsTest {
    @Test
    fun `a board with members on it is a conflict that counts them`() {
        val refusal = BoardHoldsMembers(number = 12, members = 5)

        assertThat(refusal.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(refusal.code).isEqualTo("BoardHoldsMembers")
        assertThat(refusal.facts).containsEntry("number", 12).containsEntry("members", 5L)
    }
}
