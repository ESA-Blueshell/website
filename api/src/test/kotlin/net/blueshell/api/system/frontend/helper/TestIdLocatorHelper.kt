package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object TestIdLocatorHelper {
    fun byTestId(page: Page, testId: String): Locator {
        return page.locator("[data-testid='$testId']").first()
    }

    fun byTestId(scope: Locator, testId: String): Locator {
        return scope.locator("[data-testid='$testId']").first()
    }

    fun byTestIdWithAttribute(scope: Locator, testId: String, attribute: String, value: String): Locator {
        return scope.locator("[data-testid='$testId'][$attribute='$value']").first()
    }

    fun byTestIdPrefix(page: Page, testIdPrefix: String): Locator {
        return page.locator("[data-testid^='$testIdPrefix']").first()
    }

    fun textInput(page: Page, testId: String): Locator {
        return byTestId(page, testId).locator("input").first()
    }

    fun textInput(scope: Locator, testId: String): Locator {
        return byTestId(scope, testId).locator("input").first()
    }
}
