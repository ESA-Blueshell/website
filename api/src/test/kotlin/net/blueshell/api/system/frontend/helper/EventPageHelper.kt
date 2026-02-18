package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

object EventPageHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/events")
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

    fun eventCard(page: Page, eventTitle: String): Locator {
        return page.locator(".v-card:has-text(\"$eventTitle\")").first()
    }

    fun clickCardIcon(page: Page, eventTitle: String, iconClass: String) {
        eventCard(page, eventTitle).locator("button:has(i.$iconClass)").first().click()
    }

    fun clickApproveButton(page: Page, eventTitle: String, label: String) {
        eventCard(page, eventTitle).getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            Locator.GetByRoleOptions().setName(label).setExact(false)
        ).first().click()
    }
}
