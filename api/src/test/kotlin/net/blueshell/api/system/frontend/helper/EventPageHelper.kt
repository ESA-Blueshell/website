package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object EventPageHelper {
    private const val CALENDAR_SUBSCRIBE_BUTTON_TEST_ID = "event-calendar-subscribe-btn"
    private const val CALENDAR_MONTH_TITLE_TEST_ID = "event-calendar-month-title"
    private const val CALENDAR_NEXT_MONTH_BUTTON_TEST_ID = "event-calendar-next-month-btn"
    private const val CALENDAR_PREV_MONTH_BUTTON_TEST_ID = "event-calendar-prev-month-btn"
    private const val CALENDAR_TODAY_BUTTON_TEST_ID = "event-calendar-today-btn"

    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/events")
        page.waitForURL("**/events**")
    }

    fun openWithGuestAccessToken(page: Page, frontendUrl: String, accessToken: String) {
        page.navigate("$frontendUrl/events#accessToken=$accessToken")
        page.waitForURL("**/events**")
    }

    fun subscribeLink(page: Page): Locator {
        return TestIdLocatorHelper.byTestId(page, CALENDAR_SUBSCRIBE_BUTTON_TEST_ID)
    }

    fun monthTitle(page: Page): String {
        return TestIdLocatorHelper.byTestId(page, CALENDAR_MONTH_TITLE_TEST_ID).textContent()?.trim().orEmpty()
    }

    fun goNextMonth(page: Page) {
        TestIdLocatorHelper.byTestId(page, CALENDAR_NEXT_MONTH_BUTTON_TEST_ID).click()
    }

    fun goPrevMonth(page: Page) {
        TestIdLocatorHelper.byTestId(page, CALENDAR_PREV_MONTH_BUTTON_TEST_ID).click()
    }

    fun goToday(page: Page) {
        TestIdLocatorHelper.byTestId(page, CALENDAR_TODAY_BUTTON_TEST_ID).click()
    }

    fun waitForEventVisible(page: Page, eventTitle: String) {
        page.getByText(eventTitle, Page.GetByTextOptions().setExact(false)).first().waitFor()
    }

    fun openCalendarEvent(page: Page, eventTitle: String) {
        page.locator(".v-calendar")
            .getByText(eventTitle)
            .first()
            .click()
    }

    fun waitForEventCardVisible(page: Page, eventId: Long) {
        eventCard(page, eventId).waitFor()
    }

    fun eventCard(page: Page, eventId: Long): Locator {
        return TestIdLocatorHelper.byTestId(page, "event-card-$eventId")
    }

    fun clickApproveButton(page: Page, eventId: Long) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-approve-btn-$eventId").click()
    }

    fun clickDeleteEventButton(page: Page, eventId: Long) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-delete-btn-$eventId").click()
    }

    fun clickSignUpToggleButton(page: Page, eventId: Long) {
        TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-signup-toggle-btn-$eventId").click()
    }

    fun signUpForm(page: Page, eventId: Long): Locator {
        return TestIdLocatorHelper.byTestId(eventCard(page, eventId), "event-signup-form")
    }

    fun submitSignUpButton(page: Page, eventId: Long): Locator {
        return TestIdLocatorHelper.byTestId(signUpForm(page, eventId), "event-signup-submit-btn")
    }

    fun submitSignUpButtonForMode(page: Page, eventId: Long, mode: String): Locator {
        return TestIdLocatorHelper.byTestIdWithAttribute(
            signUpForm(page, eventId),
            "event-signup-submit-btn",
            "data-signup-mode",
            mode
        )
    }

    fun waitForSignUpMode(page: Page, eventId: Long, mode: String) {
        submitSignUpButtonForMode(page, eventId, mode).waitFor()
    }

    fun deleteSignUpButton(page: Page, eventId: Long): Locator {
        return TestIdLocatorHelper.byTestId(signUpForm(page, eventId), "event-signup-delete-btn")
    }

    fun clickSubmitSignUpButton(page: Page, eventId: Long) {
        submitSignUpButton(page, eventId).click()
    }

    fun clickDeleteSignUpButton(page: Page, eventId: Long) {
        deleteSignUpButton(page, eventId).click()
    }
}
