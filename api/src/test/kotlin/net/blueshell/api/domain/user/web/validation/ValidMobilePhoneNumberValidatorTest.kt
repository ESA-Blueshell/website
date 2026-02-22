package net.blueshell.api.domain.user.web.validation

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import jakarta.validation.ConstraintValidatorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ValidMobilePhoneNumberValidatorTest {

    private val phoneNumberUtil = mock<PhoneNumberUtil>()
    private val validator = ValidMobilePhoneNumberValidator(phoneNumberUtil)
    private val context = mock<ConstraintValidatorContext>()

    @Test
    fun `accepts null and blank input`() {
        assertThat(validator.isValid(null, context)).isTrue()
        assertThat(validator.isValid("   ", context)).isTrue()
    }

    @Test
    fun `accepts valid mobile number`() {
        val parsed = Phonenumber.PhoneNumber()
        whenever(phoneNumberUtil.parse("+31612345678", null)).thenReturn(parsed)
        whenever(phoneNumberUtil.isValidNumber(parsed)).thenReturn(true)
        whenever(phoneNumberUtil.getNumberType(parsed)).thenReturn(PhoneNumberUtil.PhoneNumberType.MOBILE)

        assertThat(validator.isValid("+31612345678", context)).isTrue()
    }

    @Test
    fun `accepts fixed-line-or-mobile numbers`() {
        val parsed = Phonenumber.PhoneNumber()
        whenever(phoneNumberUtil.parse("+12025550123", null)).thenReturn(parsed)
        whenever(phoneNumberUtil.isValidNumber(parsed)).thenReturn(true)
        whenever(phoneNumberUtil.getNumberType(parsed)).thenReturn(PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE)

        assertThat(validator.isValid("+12025550123", context)).isTrue()
    }

    @Test
    fun `rejects valid but non-mobile number type`() {
        val parsed = Phonenumber.PhoneNumber()
        whenever(phoneNumberUtil.parse("+31531234567", null)).thenReturn(parsed)
        whenever(phoneNumberUtil.isValidNumber(parsed)).thenReturn(true)
        whenever(phoneNumberUtil.getNumberType(parsed)).thenReturn(PhoneNumberUtil.PhoneNumberType.FIXED_LINE)

        assertThat(validator.isValid("+31531234567", context)).isFalse()
    }

    @Test
    fun `rejects invalid parsed number`() {
        val parsed = Phonenumber.PhoneNumber()
        whenever(phoneNumberUtil.parse("+31600000000", null)).thenReturn(parsed)
        whenever(phoneNumberUtil.isValidNumber(parsed)).thenReturn(false)
        whenever(phoneNumberUtil.getNumberType(parsed)).thenReturn(PhoneNumberUtil.PhoneNumberType.MOBILE)

        assertThat(validator.isValid("+31600000000", context)).isFalse()
    }

    @Test
    fun `rejects parse failures`() {
        whenever(phoneNumberUtil.parse("not-a-number", null)).thenThrow(NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "bad"))

        assertThat(validator.isValid("not-a-number", context)).isFalse()
    }
}
