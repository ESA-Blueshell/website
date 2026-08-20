package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object MembershipSignUpHelper {
    private const val DETAILS_NEXT_BUTTON_TEST_ID = "membership-details-next-btn"
    private const val ADDRESS_NEXT_BUTTON_TEST_ID = "membership-address-next-btn"
    private const val ADDRESS_BACK_BUTTON_TEST_ID = "membership-address-back-btn"
    private const val CONDITIONS_BACK_BUTTON_TEST_ID = "membership-conditions-back-btn"
    private const val CONDITIONS_SUBMIT_BUTTON_TEST_ID = "membership-conditions-submit-btn"
    private const val CONFIRM_EMAIL_STEP_TEST_ID = "email-confirm-step"
    private const val CORRECT_EMAIL_BUTTON_TEST_ID = "email-confirm-correct-btn"
    private const val CORRECTED_EMAIL_FIELD_TEST_ID = "email-confirm-address-field"
    private const val CORRECTED_EMAIL_SUBMIT_TEST_ID = "email-confirm-address-submit-btn"
    private const val COMPLETE_PANEL_TEST_ID = "membership-complete-panel"
    private const val RESEND_BUTTON_TEST_ID = "email-confirm-resend-btn"
    private const val CONFIRM_BACK_BUTTON_TEST_ID = "email-confirm-back-btn"
    private const val CONDITIONS_ACCEPTED_TEST_ID = "membership-conditions-accepted"
    private const val CONDITIONS_CONTINUE_BUTTON_TEST_ID = "membership-conditions-continue-btn"

    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/membership/signup")
        page.waitForURL("**/membership/signup**")
    }

    fun detailsNextButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, DETAILS_NEXT_BUTTON_TEST_ID)

    fun addressNextButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, ADDRESS_NEXT_BUTTON_TEST_ID)

    fun addressBackButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, ADDRESS_BACK_BUTTON_TEST_ID)

    fun conditionsBackButton(page: Page): Locator =
        TestIdLocatorHelper.byTestId(page, CONDITIONS_BACK_BUTTON_TEST_ID)

    fun conditionsSubmitButton(page: Page): Locator =
        TestIdLocatorHelper.byTestId(page, CONDITIONS_SUBMIT_BUTTON_TEST_ID)

    fun confirmEmailStep(page: Page): Locator = TestIdLocatorHelper.byTestId(page, CONFIRM_EMAIL_STEP_TEST_ID)

    fun correctEmailButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, CORRECT_EMAIL_BUTTON_TEST_ID)

    fun correctedEmailField(page: Page): Locator =
        TestIdLocatorHelper.byTestId(page, CORRECTED_EMAIL_FIELD_TEST_ID).locator("input").first()

    fun correctedEmailSubmitButton(page: Page): Locator =
        TestIdLocatorHelper.byTestId(page, CORRECTED_EMAIL_SUBMIT_TEST_ID)

    fun completePanel(page: Page): Locator = TestIdLocatorHelper.byTestId(page, COMPLETE_PANEL_TEST_ID)

    fun resendButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, RESEND_BUTTON_TEST_ID)

    fun confirmBackButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, CONFIRM_BACK_BUTTON_TEST_ID)

    fun conditionsAccepted(page: Page): Locator = TestIdLocatorHelper.byTestId(page, CONDITIONS_ACCEPTED_TEST_ID)

    fun conditionsContinueButton(page: Page): Locator =
        TestIdLocatorHelper.byTestId(page, CONDITIONS_CONTINUE_BUTTON_TEST_ID)
}
