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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
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
        mark: Long = 0,
    ) = RaisedAlert(key, AlertKind.JOB_DEAD, null, null, 1, since, mark)

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
                source(AlertAudience.BOARD, alert("board-old", now.minusSeconds(60), mark = 5), alert("board-undated", null)),
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
        whenever(hidden.findAllByUserId(7)).thenReturn(listOf(HiddenAlert(7, "board-old", 5, now), HiddenAlert(7, "gone", 0, now)))

        val listed = controller.listAlerts()

        assertThat(listed.map { it.key to it.hidden }).containsExactly("board-old" to true, "board-undated" to false)
        verify(hidden).forget(7, setOf("gone"))
        val json = JsonMapper.builder().findAndAddModules().build()
        assertThat(json.writeValueAsString(listed.first()))
            .contains("\"kind\":\"JOB_DEAD\"", "\"count\":1", "\"subjectId\":null", "\"subjectLabel\":null", "\"since\"")
    }

    @Test
    fun `a hide covers the causes there were when it was taken, and no later one`() {
        signedIn(Role.BOARD)

        whenever(hidden.findAllByUserId(7)).thenReturn(listOf(HiddenAlert(7, "board-old", 9, now)))
        assertThat(controller.listAlerts().first().hidden).isTrue()

        whenever(hidden.findAllByUserId(7)).thenReturn(listOf(HiddenAlert(7, "board-old", 4, now)))
        assertThat(controller.listAlerts().first().hidden).isFalse()
    }

    @Test
    fun `reading writes nothing while every hide still has its alert`() {
        signedIn(Role.BOARD)
        whenever(hidden.findAllByUserId(7)).thenReturn(listOf(HiddenAlert(7, "board-old", 5, now)))

        controller.listAlerts()

        verify(hidden, never()).forget(any(), any())
    }

    @Test
    fun `hiding stores the alert at its mark for the reader, and refuses one they cannot see`() {
        signedIn(Role.BOARD)

        controller.hideAlert(AlertKeyRequest("board-old"))

        verify(hidden).hide(7, "board-old", 5, now)
        assertThatThrownBy { controller.hideAlert(AlertKeyRequest("admin-new")) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode).isEqualTo(HttpStatus.NOT_FOUND) }
        verify(hidden, never()).hide(7, "admin-new", 0, now)
    }

    @Test
    fun `showing drops the reader's hide`() {
        signedIn(Role.BOARD)

        controller.showAlert(AlertKeyRequest("board-old"))

        verify(hidden).show(7, "board-old")
    }

    @Test
    fun `nobody signed in is refused`() {
        whenever(reader.currentUser()).thenReturn(null)

        assertThatThrownBy { controller.listAlerts() }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) }
        verify(hidden, never()).findAllByUserId(any())
    }

    @Test
    fun `hibernate can build an empty hide to fill, and a hide says whose it is and when it was taken`() {
        val empty = HiddenAlert::class.java.getDeclaredConstructor().newInstance()
        assertThat(empty.id).isNull()
        val hide = HiddenAlert(7, "board-old", 5, now)
        assertThat(hide.userId to hide.hiddenAt).isEqualTo(7L to now)
    }
}
