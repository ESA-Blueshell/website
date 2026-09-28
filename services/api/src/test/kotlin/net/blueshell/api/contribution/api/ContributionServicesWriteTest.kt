package net.blueshell.api.contribution.api

import jakarta.persistence.EntityManager
import net.blueshell.api.contribution.domain.ContributionReminderService
import net.blueshell.api.contribution.domain.IncassoNotificationService
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionPeriodRepository
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

/** The reads and writes the contribution services took over from BaseModelService. */
class ContributionServicesWriteTest {
    private val manager = mock<EntityManager>()

    private fun <S : Any> S.withEntityManager(): S =
        apply {
            javaClass
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(this, manager)
        }

    @Test
    fun `a period is read, listed, written back and removed`() {
        val period = mock<ContributionPeriod>().also { whenever(it.id).thenReturn(4) }
        val repository =
            mock<ContributionPeriodRepository> {
                on { saveAndFlush(any<ContributionPeriod>()) } doAnswer { it.getArgument(0) }
            }
        whenever(repository.findById(4)).thenReturn(Optional.of(period))
        whenever(repository.findById(5)).thenReturn(Optional.empty())
        whenever(repository.findAll()).thenReturn(mutableListOf(period))
        whenever(repository.existsById(4)).thenReturn(true)
        val service = ContributionPeriodService(repository, mock<TrackedEventPublisher>()).withEntityManager()

        assertThat(service.findAll()).containsExactly(period)
        assertThat(service.existsById(4)).isTrue()
        assertThatThrownBy { service.findById(5) }.isInstanceOf(ResponseStatusException::class.java)
        service.create(period)
        service.update(period)
        service.deleteById(4)

        verify(manager, times(2)).refresh(period)
        verify(repository).delete(period)
    }

    @Test
    fun `a contribution is written back and removed, by itself or by its id`() {
        val id = Contribution.Id(1, 2)
        val contribution = mock<Contribution>().also { whenever(it.id).thenReturn(id) }
        val repository =
            mock<ContributionRepository> {
                on { saveAndFlush(any<Contribution>()) } doAnswer { it.getArgument(0) }
            }
        whenever(repository.findById(id)).thenReturn(Optional.of(contribution))
        whenever(repository.findById(Contribution.Id(1, 3))).thenReturn(Optional.empty())
        whenever(repository.existsById(id)).thenReturn(true)
        val service = ContributionService(repository, mock(), mock(), mock<TrackedEventPublisher>()).withEntityManager()

        service.create(contribution)
        service.update(contribution)
        service.delete(contribution)
        service.deleteById(id)

        verify(manager, times(2)).refresh(contribution)
        verify(repository, times(2)).delete(contribution)
        assertThatThrownBy { service.findById(Contribution.Id(1, 3)) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a reminder and a pre-notification are read by id`() {
        val reminders = mock<ContributionReminderRepository>()
        val reminder = mock<ContributionReminder>()
        whenever(reminders.findById(7)).thenReturn(Optional.of(reminder))
        whenever(reminders.findById(9)).thenReturn(Optional.empty())
        whenever(reminders.saveAndFlush(reminder)).thenReturn(reminder)
        val notifications = mock<IncassoNotificationRepository>()
        val notification = mock<IncassoNotification>()
        whenever(notifications.findById(8)).thenReturn(Optional.empty())
        whenever(notifications.findById(6)).thenReturn(Optional.of(notification))
        val reminderService = ContributionReminderService(reminders, mock(), mock()).withEntityManager()
        val notificationService = IncassoNotificationService(notifications, mock(), mock())

        assertThat(reminderService.findById(7)).isSameAs(reminder)
        assertThat(reminderService.create(reminder)).isSameAs(reminder)
        assertThatThrownBy { reminderService.findById(9) }.isInstanceOf(ResponseStatusException::class.java)
        assertThat(notificationService.findById(6)).isSameAs(notification)
        assertThatThrownBy { notificationService.findById(8) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
