package net.blueshell.api.shared.job

import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Locks in the canonical retry-classification policy so an accidental
 * addition / removal from [NonRetryableJobException.NON_RETRYABLE_EXCEPTIONS]
 * is loud at PR time. The JobExecutor reads this set on every failure
 * to decide between FAILED-immediately and exponential-backoff retry; a
 * silent change of policy would land in production without any
 * observable test diff today.
 *
 * The policy intent (mirrored in the KDoc on the set): membership means
 * "retrying will not change the outcome". This covers both validation
 * errors AND code-level coercion bugs (ClassCastException,
 * NullPointerException) — both will keep crashing on every redelivery
 * until a code or input change fixes them. The executor marks them
 * FAILED so they show up loudly on the dashboard and an operator can
 * hit Retry once the fix lands.
 */
class NonRetryableJobExceptionTest {

    @Test
    fun `validation and coercion errors are all non-retryable`() {
        assertThat(NonRetryableJobException.NON_RETRYABLE_EXCEPTIONS)
            .contains(
                NonRetryableJobException::class.java,
                ConstraintViolationException::class.java,
                IllegalArgumentException::class.java,
                NullPointerException::class.java,
                ClassCastException::class.java,
            )
    }

    @Test
    fun `no surprise entries — set has exactly the documented members`() {
        assertThat(NonRetryableJobException.NON_RETRYABLE_EXCEPTIONS)
            .containsExactlyInAnyOrder(
                NonRetryableJobException::class.java,
                ConstraintViolationException::class.java,
                IllegalArgumentException::class.java,
                NullPointerException::class.java,
                ClassCastException::class.java,
            )
    }
}
