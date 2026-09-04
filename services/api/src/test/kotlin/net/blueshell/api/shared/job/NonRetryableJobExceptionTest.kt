package net.blueshell.api.shared.job

import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins [NonRetryableJobException.NON_RETRYABLE_EXCEPTIONS], so adding to or removing from it is
 * loud at PR time rather than a silent change of retry policy. The set's own KDoc says what
 * membership means.
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
