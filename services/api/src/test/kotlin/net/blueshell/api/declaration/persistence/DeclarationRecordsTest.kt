package net.blueshell.api.declaration.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class DeclarationRecordsTest {
    private inline fun <reified T : Any> loaded(): T = T::class.java.getDeclaredConstructor().newInstance()

    @Test
    fun `each record loads through the constructor JPA uses, and an expense knows its place`() {
        assertThat(loaded<Declaration>()).isNotNull
        assertThat(loaded<DeclarationExpense>()).isNotNull
        assertThat(loaded<Booking>()).isNotNull
        assertThat(loaded<DeclarationStep>()).isNotNull

        val declaration = Declaration(12, Instant.EPOCH)
        val expense = DeclarationExpense(declaration, 2, LocalDate.of(2026, 10, 1), "Snacks", 4835, null)
        assertThat(declaration.version).isZero()
        assertThat(expense.declaration).isSameAs(declaration)
        assertThat(expense.position).isEqualTo(2)
    }
}
