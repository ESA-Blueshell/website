package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object UserFormHelper {
    private const val INITIALS_FIELD_TEST_ID = "user-form-initials-field"
    private const val FIRST_NAME_FIELD_TEST_ID = "user-form-first-name-field"
    private const val SURNAME_FIELD_TEST_ID = "user-form-last-name-field"
    private const val USERNAME_FIELD_TEST_ID = "user-form-username-field"
    private const val DISCORD_FIELD_TEST_ID = "user-form-discord-field"
    private const val EMAIL_FIELD_TEST_ID = "user-form-email-field"
    private const val PHONE_NUMBER_FIELD_TEST_ID = "user-form-phone-number-field"
    private const val PASSWORD_FIELD_TEST_ID = "user-form-password-field"
    private const val PASSWORD_REPEAT_FIELD_TEST_ID = "user-form-password-repeat-field"
    private const val DATE_OF_BIRTH_FIELD_TEST_ID = "user-form-date-of-birth-field"
    private const val GENDER_FIELD_TEST_ID = "user-form-gender-field"
    private const val STUDENT_NUMBER_FIELD_TEST_ID = "user-form-student-number-field"
    private const val SUBMIT_BUTTON_TEST_ID = "user-form-submit-btn"

    data class Fields(
        val initials: String? = null,
        val firstName: String? = null,
        val surname: String? = null,
        val username: String? = null,
        val discord: String? = null,
        val email: String? = null,
        val phoneNumber: String? = null,
        val password: String? = null,
        val repeatedPassword: String? = null,
        val dateOfBirth: String? = null,
        val gender: String? = null,
        val studentNumber: String? = null
    )

    fun initialsInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, INITIALS_FIELD_TEST_ID)
    }

    fun firstNameInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, FIRST_NAME_FIELD_TEST_ID)
    }

    fun surnameInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, SURNAME_FIELD_TEST_ID)
    }

    fun usernameInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, USERNAME_FIELD_TEST_ID)
    }

    fun discordInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, DISCORD_FIELD_TEST_ID)
    }

    fun emailInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, EMAIL_FIELD_TEST_ID)
    }

    fun phoneNumberInput(page: Page): Locator {
        val phoneField = TestIdLocatorHelper.byTestId(page, PHONE_NUMBER_FIELD_TEST_ID)
        val byPlaceholder = phoneField.locator("input[placeholder='Phone Number']").first()
        if (byPlaceholder.count() > 0) {
            return byPlaceholder
        }
        val byName = phoneField.locator("input[name='phoneNumber']").first()
        if (byName.count() > 0) {
            return byName
        }
        return phoneField.locator("input").last()
    }

    fun passwordInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, PASSWORD_FIELD_TEST_ID)
    }

    fun repeatedPasswordInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, PASSWORD_REPEAT_FIELD_TEST_ID)
    }

    fun dateOfBirthInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, DATE_OF_BIRTH_FIELD_TEST_ID)
    }

    fun genderInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, GENDER_FIELD_TEST_ID)
    }

    fun studentNumberInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, STUDENT_NUMBER_FIELD_TEST_ID)
    }

    fun submitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, SUBMIT_BUTTON_TEST_ID)
    }

    fun fill(page: Page, fields: Fields) {
        fields.initials?.let { initialsInput(page).fill(it) }
        fields.firstName?.let { firstNameInput(page).fill(it) }
        fields.surname?.let { surnameInput(page).fill(it) }
        fields.username?.let { usernameInput(page).fill(it) }
        fields.discord?.let { discordInput(page).fill(it) }
        fields.email?.let { emailInput(page).fill(it) }
        fields.phoneNumber?.let { phoneNumberInput(page).fill(it) }
        fields.password?.let { passwordInput(page).fill(it) }
        fields.repeatedPassword?.let { repeatedPasswordInput(page).fill(it) }
        fields.dateOfBirth?.let { dateOfBirthInput(page).fill(it) }
        fields.gender?.let { genderInput(page).fill(it) }
        fields.studentNumber?.let { studentNumberInput(page).fill(it) }
    }
}
