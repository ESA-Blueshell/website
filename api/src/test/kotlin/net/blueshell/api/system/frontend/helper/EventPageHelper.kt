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
        return page.locator("[data-testid='event-card-$eventId']").first()
    }

    fun clickApproveButton(page: Page, eventId: Long) {
        eventCard(page, eventId).locator("[data-testid='event-approve-btn-$eventId']").first().click()
    }

    fun clickDeleteEventButton(page: Page, eventId: Long) {
        eventCard(page, eventId).locator("[data-testid='event-delete-btn-$eventId']").first().click()
    }

    fun clickSignUpToggleButton(page: Page, eventId: Long) {
        eventCard(page, eventId).locator("[data-testid='event-signup-toggle-btn-$eventId']").first().click()
    }

    fun signUpForm(page: Page, eventId: Long): Locator {
        return eventCard(page, eventId).locator("[data-testid='event-signup-form']").first()
    }

    fun submitSignUpButton(page: Page, eventId: Long): Locator {
        return signUpForm(page, eventId).locator("[data-testid='event-signup-submit-btn']").first()
    }

    fun submitSignUpButtonForMode(page: Page, eventId: Long, mode: String): Locator {
        return signUpForm(page, eventId).locator(
            "[data-testid='event-signup-submit-btn'][data-signup-mode='$mode']"
        ).first()
    }

    fun waitForSignUpMode(page: Page, eventId: Long, mode: String) {
        submitSignUpButtonForMode(page, eventId, mode).waitFor()
    }

    fun deleteSignUpButton(page: Page, eventId: Long): Locator {
        return signUpForm(page, eventId).locator("[data-testid='event-signup-delete-btn']").first()
    }

    fun clickSubmitSignUpButton(page: Page, eventId: Long) {
        submitSignUpButton(page, eventId).click()
    }

    fun clickDeleteSignUpButton(page: Page, eventId: Long) {
        deleteSignUpButton(page, eventId).click()
    }
}
