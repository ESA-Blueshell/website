package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.domain.BulkContributionEmailUseCases
import net.blueshell.api.contribution.domain.ContributionEmailResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BulkContributionEmailControllerTest {
    private val useCases: BulkContributionEmailUseCases = mock()
    private val controller = BulkContributionEmailController(useCases, mock())

    @Test
    fun `a send reports the members it skipped as already notified`() {
        whenever(useCases.send(eq(3L), any(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(
                ContributionEmailResult(
                    remindersSent = 1,
                    incassoNotificationsSent = 2,
                    notWrittenTo = 0,
                    incassoNotificationsSkipped = 4,
                ),
            )

        val result = controller.sendPaymentEmails(SendPaymentEmailsRequest(contributionPeriodId = 3, userIds = listOf(7)))

        assertThat(result.incassoNotificationsSkipped).isEqualTo(4)
        assertThat(result.incassoNotificationsSent).isEqualTo(2)
    }
}
