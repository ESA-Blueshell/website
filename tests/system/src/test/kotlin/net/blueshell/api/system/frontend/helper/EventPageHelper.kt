package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object EventPageHelper {
    private const val CALENDAR_SUBSCRIBE_BUTTON_TEST_ID = "event-calendar-subscribe-btn"

    fun open(
        page: Page,
        frontendUrl: String,
    ) {
        page.navigate("$frontendUrl/events")
        page.waitForURL("**/events**")
    }

    fun openWithGuestAccessToken(
        page: Page,
        frontendUrl: String,
        accessToken: String,
    ) {
        page.navigate("$frontendUrl/events#accessToken=$accessToken")
        page.waitForURL("**/events**")
    }

    fun subscribeLink(page: Page): Locator = TestIdLocatorHelper.byTestId(page, CALENDAR_SUBSCRIBE_BUTTON_TEST_ID)

    fun waitForEventVisible(
        page: Page,
        eventTitle: String,
    ) {
        page.getByText(eventTitle, Page.GetByTextOptions().setExact(false)).first().waitFor()
    }

    fun waitForEventCardVisible(
        page: Page,
        eventId: Long,
    ) {
        eventCard(page, eventId).waitFor()
    }

    fun eventCard(
        page: Page,
        eventId: Long,
    ): Locator = TestIdLocatorHelper.byTestId(page, "event-card-$eventId")

    fun clickApproveButton(
        page: Page,
        eventId: Long,
    ) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-approve-btn-$eventId").click()
    }

    fun clickEditEventButton(
        page: Page,
        eventId: Long,
    ) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-edit-btn-$eventId").click()
    }

    fun clickDeleteEventButton(
        page: Page,
        eventId: Long,
    ) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-delete-btn-$eventId").click()
    }

    fun clickSignUpToggleButton(
        page: Page,
        eventId: Long,
    ) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-signup-toggle-btn-$eventId").click()
    }

    fun signUpForm(
        page: Page,
        eventId: Long,
    ): Locator = TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-signup-form")

    fun submitSignUpButton(
        page: Page,
        eventId: Long,
    ): Locator = TestIdLocatorHelper.byTestId(signUpForm(page, eventId), "event-signup-submit-btn")

    fun submitSignUpButtonForMode(
        page: Page,
        eventId: Long,
        mode: String,
    ): Locator =
        TestIdLocatorHelper.byTestIdWithAttribute(
            signUpForm(page, eventId),
            "event-signup-submit-btn",
            "data-signup-mode",
            mode,
        )

    fun waitForSignUpMode(
        page: Page,
        eventId: Long,
        mode: String,
    ) {
        submitSignUpButtonForMode(page, eventId, mode).waitFor()
    }

    fun deleteSignUpButton(
        page: Page,
        eventId: Long,
    ): Locator = TestIdLocatorHelper.byTestId(signUpForm(page, eventId), "event-signup-delete-btn")

    fun clickSubmitSignUpButton(
        page: Page,
        eventId: Long,
    ) {
        submitSignUpButton(page, eventId).click()
    }
}
