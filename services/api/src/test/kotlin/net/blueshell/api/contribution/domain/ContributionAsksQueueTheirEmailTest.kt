package net.blueshell.api.contribution.domain

import jakarta.persistence.EntityManager
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.service.BaseModelService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** A recorded reminder or pre-notification queues the email for the row it wrote. */
class ContributionAsksQueueTheirEmailTest {
    private val jobs: JobQueue = mock()

    private fun <S : BaseModelService<*, *, *>> S.withEntityManager(): S =
        apply {
            BaseModelService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(this, mock<EntityManager>())
        }

    @Test
    fun `a reminder queues its own email`() {
        val reminder: ContributionReminder = mock { on { id } doReturn 5 }
        val repository: ContributionReminderRepository = mock { on { saveAndFlush(reminder) } doReturn reminder }

        val written = ContributionReminderService(repository, mock(), jobs).withEntityManager().record(reminder)

        assertThat(written).isSameAs(reminder)
        verify(jobs).runAsync(EmailJobs.ContributionReminder, EmailJobs.ContributionReminderPayload(5), JobTrigger.SITE_ACTION)
    }

    @Test
    fun `a pre-notification queues its own email`() {
        val notification: IncassoNotification = mock { on { id } doReturn 6 }
        val repository: IncassoNotificationRepository = mock { on { saveAndFlush(notification) } doReturn notification }

        val written = IncassoNotificationService(repository, mock(), jobs).withEntityManager().record(notification)

        assertThat(written).isSameAs(notification)
        verify(jobs).runAsync(EmailJobs.IncassoNotification, EmailJobs.IncassoNotificationPayload(6), JobTrigger.SITE_ACTION)
    }
}
