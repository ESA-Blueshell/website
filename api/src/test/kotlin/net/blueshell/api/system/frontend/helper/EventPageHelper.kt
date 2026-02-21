package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

object EventPageHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/events")
        page.waitForURL("**/events**")
    }

    fun openWithGuestAccessToken(page: Page, frontendUrl: String, accessToken: String) {
        page.navigate("$frontendUrl/events#accessToken=$accessToken")
        page.waitForURL("**/events**")
    }

    fun subscribeLink(page: Page): Locator {
        return page.getByText("Subscribe to calendar", Page.GetByTextOptions().setExact(false)).first()
    }

    fun monthTitle(page: Page): String {
        return page.locator(".toolbar-title").first().textContent()?.trim().orEmpty()
    }

    fun goNextMonth(page: Page) {
        page.locator("button:has(i.mdi-chevron-right)").first().click()
    }

    fun goPrevMonth(page: Page) {
        page.locator("button:has(i.mdi-chevron-left)").first().click()
    }

    fun goToday(page: Page) {
        page.getByRole(
            AriaRole.BUTTON,
            Page.GetByRoleOptions().setName("Today").setExact(true)
        ).click()
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
