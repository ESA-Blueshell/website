package net.blueshell.api.alerts.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.alerts.persistence.HiddenAlert
import net.blueshell.api.alerts.persistence.HiddenAlertRepository
import net.blueshell.api.alerts.web.AlertController
import net.blueshell.api.alerts.web.AlertKeyRequest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AlertsTest {
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val hidden: HiddenAlertRepository = mock()
    private val reader: CurrentUserProvider = mock()

    private fun alert(
        key: String,
        since: Instant?,
    ) = RaisedAlert(key, AlertKind.JOB_DEAD, null, null, 1, since)

    private fun source(
        audience: AlertAudience,
        vararg alerts: RaisedAlert,
    ) = object : AlertSource {
        override val audience = audience

        override fun raised() = alerts.toList()
    }

    private val alerts =
        Alerts(
            listOf(
                source(AlertAudience.BOARD, alert("board-old", now.minusSeconds(60)), alert("board-undated", null)),
                source(AlertAudience.ADMIN, alert("admin-new", now)),
            ),
            hidden,
            Clock.fixed(now, ZoneOffset.UTC),
        )
    private val controller = AlertController(alerts, reader)

    private fun signedIn(vararg roles: Role) = CurrentUser(7, roles.toSet(), null).also { whenever(reader.currentUser()).thenReturn(it) }

    @Test
    fun `the board and the treasurer read board alerts only, and an admin reads them all, newest first`() {
        signedIn(Role.BOARD)
        assertThat(controller.listAlerts().map { it.key }).containsExactly("board-old", "board-undated")
        signedIn(Role.TREASURER)
        assertThat(controller.listAlerts().map { it.key }).containsExactly("board-old", "board-undated")
        signedIn(Role.ADMIN)
        assertThat(controller.listAlerts().map { it.key }).containsExactly("admin-new", "board-old", "board-undated")
    }

    @Test
    fun `an alert the reader hid is marked hidden, and a hide whose alert cleared is dropped`() {
        signedIn(Role.BOARD)
        val kept = HiddenAlert(7, "board-old", now)
        val stale = HiddenAlert(7, "gone", now)
        whenever(hidden.findAllByUserId(7)).thenReturn(listOf(kept, stale))

        val listed = controller.listAlerts()

        assertThat(listed.map { it.key to it.hidden }).containsExactly("board-old" to true, "board-undated" to false)
        val dropped = argumentCaptor<Iterable<HiddenAlert>>()
        verify(hidden).deleteAll(dropped.capture())
        assertThat(dropped.firstValue).containsExactly(stale)
        val json = JsonMapper.builder().findAndAddModules().build()
        assertThat(json.writeValueAsString(listed.first()))
            .contains("\"kind\":\"JOB_DEAD\"", "\"count\":1", "\"subjectId\":null", "\"subjectLabel\":null", "\"since\"")
    }

    @Test
    fun `hiding stores the alert for the reader once, and refuses one they cannot see`() {
        signedIn(Role.BOARD)
        controller.hideAlert(AlertKeyRequest("board-old"))
        val saved = argumentCaptor<HiddenAlert>()
        verify(hidden).save(saved.capture())
        assertThat(saved.firstValue.userId).isEqualTo(7)
        assertThat(saved.firstValue.alertKey).isEqualTo("board-old")
        assertThat(saved.firstValue.hiddenAt).isEqualTo(now)

        whenever(hidden.findByUserIdAndAlertKey(7, "board-old")).thenReturn(saved.firstValue)
        controller.hideAlert(AlertKeyRequest("board-old"))
        verify(hidden).save(any())

        assertThatThrownBy { controller.hideAlert(AlertKeyRequest("admin-new")) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `showing drops the reader's hide, and does nothing where there is none`() {
        signedIn(Role.BOARD)
        val hide = HiddenAlert(7, "board-old", now)
        whenever(hidden.findByUserIdAndAlertKey(7, "board-old")).thenReturn(hide)

        controller.showAlert(AlertKeyRequest("board-old"))
        controller.showAlert(AlertKeyRequest("never"))

        verify(hidden).delete(hide)
    }

    @Test
    fun `nobody signed in is refused`() {
        whenever(reader.currentUser()).thenReturn(null)

        assertThatThrownBy { controller.listAlerts() }.isInstanceOf(ResponseStatusException::class.java)
        verify(hidden, never()).findAllByUserId(any())
        val empty = HiddenAlert::class.java.getDeclaredConstructor().newInstance()
        assertThat(empty.id).isNull()
    }
}
