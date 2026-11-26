package net.blueshell.api.jobs.api

import io.mockk.mockkClass
import net.blueshell.api.auth.domain.RecoveryEmailHandler
import net.blueshell.api.auth.domain.RoleChangeEmailJob
import net.blueshell.api.auth.domain.SecurityNotificationEmailJob
import net.blueshell.api.contribution.domain.ContributionReminderEmailJob
import net.blueshell.api.contribution.domain.IncassoNotificationEmailJob
import net.blueshell.api.contribution.domain.JoiningContributionEmailJob
import net.blueshell.api.event.domain.EventSignUpAddedEmailJob
import net.blueshell.api.event.domain.EventSignUpRemovedEmailJob
import net.blueshell.api.event.domain.EventSignupEmailJob
import net.blueshell.api.jobs.domain.JobHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class EmailRetryScheduleTest {
    @ParameterizedTest
    @ValueSource(
        classes = [
            RecoveryEmailHandler::class,
            RoleChangeEmailJob::class,
            SecurityNotificationEmailJob::class,
            ContributionReminderEmailJob::class,
            IncassoNotificationEmailJob::class,
            JoiningContributionEmailJob::class,
            EventSignUpAddedEmailJob::class,
            EventSignUpRemovedEmailJob::class,
            EventSignupEmailJob::class,
        ],
    )
    fun `email handlers retry for about an hour`(handler: Class<*>) {
        assertThat(instantiate(handler.kotlin).retrySchedule).isEqualTo(EMAIL_RETRY_SCHEDULE)
        assertThat(EMAIL_RETRY_SCHEDULE.maxRetries).isEqualTo(8)
        assertThat(EMAIL_RETRY_SCHEDULE.initialBackoff).isEqualTo(Duration.ofSeconds(30))
        assertThat(EMAIL_RETRY_SCHEDULE.multiplier).isEqualTo(2.0)
        assertThat(EMAIL_RETRY_SCHEDULE.maxBackoff).isEqualTo(Duration.ofMinutes(15))
    }

    private fun instantiate(type: KClass<*>): JobHandler {
        val constructor = requireNotNull(type.primaryConstructor)
        val args =
            constructor.parameters.filterNot { it.isOptional }.associateWith { parameter ->
                val kind = parameter.type.classifier as KClass<*>
                if (kind == String::class) "https://example.test" else mockkClass(kind, relaxed = true)
            }
        return constructor.callBy(args) as JobHandler
    }
}
