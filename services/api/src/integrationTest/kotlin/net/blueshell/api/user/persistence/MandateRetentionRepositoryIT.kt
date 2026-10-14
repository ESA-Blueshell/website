package net.blueshell.api.user.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

@SpringBootTest
class MandateRetentionRepositoryIT : UserTestSupport() {
    @Autowired
    private lateinit var retention: MandateRetentionRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun endedOn(userId: Long): LocalDate? =
        jdbc.queryForObject("SELECT mandate_ended_on FROM payment_details WHERE user_id = ?", LocalDate::class.java, userId)

    @Test
    fun `erasing an account ends a standing mandate today, one ending ahead included, and leaves an ended one as it was`() {
        val today = LocalDate.of(2026, 10, 15)
        val (standing, ahead, ended) = List(3) { requireNotNull(createUserWithRole(Role.MEMBER).id) }
        listOf(standing, ahead, ended).forEach { userId ->
            jdbc.update(INSERT, userId, "BLUESHELL-$userId", today)
        }
        jdbc.update("UPDATE payment_details SET mandate_ended_on = ? WHERE user_id = ?", today.plusMonths(2), ahead)
        jdbc.update("UPDATE payment_details SET mandate_ended_on = ? WHERE user_id = ?", today.minusMonths(2), ended)

        transactionTemplate.execute { listOf(standing, ahead, ended).forEach { retention.stopCollecting(it, today) } }

        assertThat(endedOn(standing)).isEqualTo(today)
        assertThat(endedOn(ahead)).isEqualTo(today)
        assertThat(endedOn(ended)).isEqualTo(today.minusMonths(2))
    }

    private companion object {
        const val INSERT = "INSERT INTO payment_details (user_id, mandate_reference, mandate_signed_on) VALUES (?, ?, ?)"
    }
}
