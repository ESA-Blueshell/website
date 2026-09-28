package net.blueshell.api.user.domain

import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class RoleRefusalsTest {
    @Test
    fun `a role no admin hands out is a bad request naming it`() {
        val refusal = RoleNotAssignable(Role.SYSTEM)

        assertThat(refusal.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(refusal.code).isEqualTo("RoleNotAssignable")
        assertThat(refusal.facts).containsEntry("role", "SYSTEM")
    }

    @Test
    fun `taking the last administrator is a conflict with no facts`() {
        val refusal = LastAdministrator()

        assertThat(refusal.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(refusal.code).isEqualTo("LastAdministrator")
        assertThat(refusal.facts).isEmpty()
    }
}
