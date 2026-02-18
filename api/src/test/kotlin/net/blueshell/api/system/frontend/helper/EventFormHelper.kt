package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Paths

object EventFormHelper {
    fun openCreatePage(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/events/create")
        page.waitForURL("**/events/create**")
        page.getByLabel("Event name").first().waitFor()
    }

    fun openEditPage(page: Page, frontendUrl: String, eventId: Long) {
        page.navigate("$frontendUrl/events/edit/$eventId")
        page.waitForURL("**/events/edit/$eventId**")
        page.getByLabel("Event name").first().waitFor()
    }

    fun fillRequiredFields(
        page: Page,
        title: String,
        location: String,
        description: String
    ) {
        page.getByLabel("Event name").fill(title)
        page.getByLabel("Location").fill(location)
        page.getByLabel("Description").fill(description)
    }

    fun openCommitteeSelect(page: Page) {
        page.getByRole(AriaRole.COMBOBOX).first().click()
    }

    fun selectCommittee(page: Page, committeeName: String) {
        openCommitteeSelect(page)
        page.getByText(committeeName, Page.GetByTextOptions().setExact(true)).first().click()
    }

    fun setApproved(page: Page, approved: Boolean) {
        val checkbox = page.getByRole(
            AriaRole.CHECKBOX,
            Page.GetByRoleOptions().setName("Approved").setExact(false)
        )
        if (approved) {
            checkbox.check()
        } else {
            checkbox.uncheck()
        }
    }

    fun uploadBanner(page: Page, filePath: String) {
        page.getByLabel(
            "Promo image (Max 2MB)",
            Page.GetByLabelOptions().setExact(false)
        ).nth(1).setInputFiles(Paths.get(filePath))
    }

    fun submit(page: Page) {
        page.getByRole(
            AriaRole.BUTTON,
            Page.GetByRoleOptions().setName("Submit event").setExact(false)
        ).click()
    }
}
