package net.blueshell.api.cohort.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortRecordsTest {
    // The constructor the JPA compiler plugin adds, which is the only one Hibernate calls.
    private inline fun <reified T : Any> loaded(): T = T::class.java.getDeclaredConstructor().newInstance()

    @Test
    fun `Hibernate can build each append-only cohort record to load a row into`() {
        assertThat(loaded<TargetDeletion>().id).isNull()
    }
}
