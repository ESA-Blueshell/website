package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object MembershipSignUpHelper {
    private const val STEP_ONE_NEXT_BUTTON_TEST_ID = "membership-step1-next-btn"
    private const val STEP_TWO_RESEND_BUTTON_TEST_ID = "membership-step2-resend-btn"
    private const val STEP_THREE_NEXT_BUTTON_TEST_ID = "membership-step3-next-btn"
    private const val STEP_FOUR_COMPLETE_BUTTON_TEST_ID = "membership-step4-complete-btn"

    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/membership/signup")
        page.waitForURL("**/membership/signup**")
    }

    fun stepOneNextButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, STEP_ONE_NEXT_BUTTON_TEST_ID)
    }

    fun clickStepOneNext(page: Page) {
        stepOneNextButton(page).click()
    }

    fun stepTwoResendButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, STEP_TWO_RESEND_BUTTON_TEST_ID)
    }

    fun clickStepTwoResend(page: Page) {
        stepTwoResendButton(page).click()
    }

    fun stepThreeNextButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, STEP_THREE_NEXT_BUTTON_TEST_ID)
    }

    fun clickStepThreeNext(page: Page) {
        stepThreeNextButton(page).click()
    }

    fun stepFourCompleteButton(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, STEP_FOUR_COMPLETE_BUTTON_TEST_ID)
    }

    fun clickStepFourComplete(page: Page) {
        stepFourCompleteButton(page).click()
    }
}
