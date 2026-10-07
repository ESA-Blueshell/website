package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class BrevoFolderAlertsTest {
    private val folders = mock<TargetFolders>()
    private val brevo = mock<TargetStrategy> { on { system } doReturn TargetSystem.BREVO }
    private val clock = SettableClock().apply { set(Instant.parse("2026-10-07T10:00:00Z")) }
    private val alerts = BrevoFolderAlerts(folders, TargetStrategies(listOf(brevo)), clock)

    private fun state(
        id: String,
        name: String,
    ) = FolderState(id, name, 0)

    @Test
    fun `raises one alert naming the folders that share a name, and reads Brevo again only once the last read is old`() {
        whenever(folders.states(TargetSystem.BREVO)).thenReturn(
            listOf(state("1", "Boards"), state("2", "boards"), state("3", "Members"), state("4", "Teams"), state("5", "Teams")),
        )

        val raised = alerts.raised().single()

        assertThat(listOf(raised.kind, raised.subjectLabel, raised.count, raised.key))
            .containsExactly(AlertKind.BREVO_FOLDERS_SHARE_NAME, "Boards, Teams", 2L, "brevo-folders-share-name:boards,teams")
        alerts.raised()
        verify(folders, times(1)).states(TargetSystem.BREVO)

        whenever(folders.states(TargetSystem.BREVO)).thenReturn(listOf(state("1", "Boards")))
        clock.advance(Duration.ofMinutes(11))
        assertThat(alerts.raised()).isEmpty()
    }

    @Test
    fun `says nothing while Brevo cannot be read or is not set up`() {
        whenever(folders.states(TargetSystem.BREVO)).thenThrow(IllegalStateException("Brevo is down"))
        assertThat(alerts.raised()).isEmpty()

        assertThat(BrevoFolderAlerts(folders, TargetStrategies(emptyList()), clock).raised()).isEmpty()
        assertThat(alerts.audience.name).isEqualTo("BOARD")
    }
}
