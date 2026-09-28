package net.blueshell.api.shared.model

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.OptimisticLockingFailureException

class VersionedEntityTest {
    private class Row : VersionedEntity()

    private val row = Row().apply { version = 3 }

    @Test
    fun `accepts a write made against the version it holds`() {
        assertThatCode { row.requireVersion(3) }.doesNotThrowAnyException()
    }

    @Test
    fun `refuses a write made against an older version`() {
        assertThatThrownBy { row.requireVersion(2) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
            .hasMessage("Row is at version 3, not 2")
    }
}
