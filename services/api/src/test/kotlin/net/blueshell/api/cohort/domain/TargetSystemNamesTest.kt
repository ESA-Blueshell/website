package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TargetSystemNamesTest {
    @Test
    fun `names every system as a path reads it`() {
        assertThat(TargetSystem.entries.associateWith { it.shownName })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(TargetSystem.BREVO to "Brevo", TargetSystem.GOOGLE_CALENDAR to "Google Calendar", TargetSystem.DISCORD to "Discord"),
            )
    }
}
