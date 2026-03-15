package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object LoginDomainHelper {
    private const val LOGIN_USERNAME_FIELD_TEST_ID = "login-username-field"
    private const val LOGIN_PASSWORD_FIELD_TEST_ID = "login-password-field"
    private const val LOGIN_SUBMIT_BUTTON_TEST_ID = "login-submit-btn"
    private const val FORGOT_PASSWORD_USERNAME_FIELD_TEST_ID = "forgot-password-username-field"
    private const val FORGOT_PASSWORD_SUBMIT_BUTTON_TEST_ID = "forgot-password-submit-btn"
    private const val RESET_PASSWORD_NEW_FIELD_TEST_ID = "reset-password-new-password-field"
    private const val RESET_PASSWORD_REPEAT_FIELD_TEST_ID = "reset-password-repeat-password-field"
    private const val RESET_PASSWORD_SUBMIT_BUTTON_TEST_ID = "reset-password-submit-btn"
    private const val ACTIVATE_MEMBER_USERNAME_FIELD_TEST_ID = "activate-member-username-field"
    private const val ACTIVATE_MEMBER_PASSWORD_FIELD_TEST_ID = "activate-member-password-field"
    private const val ACTIVATE_MEMBER_REPEAT_PASSWORD_FIELD_TEST_ID = "activate-member-repeat-password-field"
    private const val ACTIVATE_MEMBER_SUBMIT_BUTTON_TEST_ID = "activate-member-submit-btn"
    private const val USER_FORM_SUBMIT_BUTTON_TEST_ID = "user-form-submit-btn"
    private const val ADDRESS_FORM_SUBMIT_BUTTON_TEST_ID = "address-form-submit-btn"

    fun loginUsernameInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, LOGIN_USERNAME_FIELD_TEST_ID)
    }

    fun loginPasswordInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, LOGIN_PASSWORD_FIELD_TEST_ID)
    }

    fun loginSubmitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, LOGIN_SUBMIT_BUTTON_TEST_ID)
    }

    fun fillLoginCredentials(page: Page, username: String, password: String) {
        loginUsernameInput(page).fill(username)
        loginPasswordInput(page).fill(password)
    }

    fun clickLoginSubmit(page: Page) {
        loginSubmitButton(page).click()
    }

    fun fillForgotPasswordUsername(page: Page, username: String) {
        forgotPasswordUsernameInput(page).fill(username)
    }

    fun forgotPasswordUsernameInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, FORGOT_PASSWORD_USERNAME_FIELD_TEST_ID)
    }

    fun forgotPasswordSubmitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, FORGOT_PASSWORD_SUBMIT_BUTTON_TEST_ID)
    }

    fun clickForgotPasswordSubmit(page: Page) {
        forgotPasswordSubmitButton(page).click()
    }

    fun fillResetPasswordForm(page: Page, password: String) {
        resetPasswordNewInput(page).fill(password)
        resetPasswordRepeatInput(page).fill(password)
    }

    fun resetPasswordNewInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, RESET_PASSWORD_NEW_FIELD_TEST_ID)
    }

    fun resetPasswordRepeatInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, RESET_PASSWORD_REPEAT_FIELD_TEST_ID)
    }

    fun resetPasswordSubmitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, RESET_PASSWORD_SUBMIT_BUTTON_TEST_ID)
    }

    fun clickResetPasswordSubmit(page: Page) {
        resetPasswordSubmitButton(page).click()
    }

    fun fillActivateMemberForm(page: Page, username: String, password: String) {
        activateMemberUsernameInput(page).fill(username)
        activateMemberPasswordInput(page).fill(password)
        activateMemberRepeatPasswordInput(page).fill(password)
    }

    fun activateMemberUsernameInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, ACTIVATE_MEMBER_USERNAME_FIELD_TEST_ID)
    }

    fun activateMemberPasswordInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, ACTIVATE_MEMBER_PASSWORD_FIELD_TEST_ID)
    }

    fun activateMemberRepeatPasswordInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, ACTIVATE_MEMBER_REPEAT_PASSWORD_FIELD_TEST_ID)
    }

    fun activateMemberSubmitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, ACTIVATE_MEMBER_SUBMIT_BUTTON_TEST_ID)
    }

    fun clickActivateMemberSubmit(page: Page) {
        activateMemberSubmitButton(page).click()
    }

    fun accountSubmitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, USER_FORM_SUBMIT_BUTTON_TEST_ID)
    }

    fun clickAccountSubmit(page: Page) {
        accountSubmitButton(page).click()
    }

    fun addressSubmitButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, ADDRESS_FORM_SUBMIT_BUTTON_TEST_ID)
    }

    fun clickAddressSubmit(page: Page) {
        addressSubmitButton(page).click()
    }
}
