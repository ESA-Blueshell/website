package net.blueshell.api.jobs.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class FoldedTriggersConverterTest {
    private val converter = FoldedTriggersConverter()

    @Test
    fun `keeps each folded trigger with who and when, and nothing as no column at all`() {
        val folded =
            listOf(
                FoldedTrigger(JobTrigger.EVENT_UPDATED, Actor.user(5, Role.BOARD), Instant.parse("2026-10-01T10:00:00.123Z")),
                FoldedTrigger(JobTrigger.MORNING_RUN, Actor.system(), Instant.parse("2026-10-02T06:00:00Z")),
            )

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(folded))).isEqualTo(folded)
        assertThat(converter.convertToDatabaseColumn(emptyList())).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isEmpty()
    }
}
