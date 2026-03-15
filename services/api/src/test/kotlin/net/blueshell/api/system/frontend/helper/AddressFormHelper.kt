package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object AddressFormHelper {
    data class Fields(
        val street: String,
        val houseNumber: String,
        val zipCode: String,
        val city: String
    )

    fun fill(page: Page, fields: Fields) {
        page.getByLabel("Street", Page.GetByLabelOptions().setExact(true)).fill(fields.street)
        page.getByLabel("House Number", Page.GetByLabelOptions().setExact(true)).fill(fields.houseNumber)
        page.getByLabel("Zipcode", Page.GetByLabelOptions().setExact(true)).fill(fields.zipCode)
        page.getByLabel("City", Page.GetByLabelOptions().setExact(true)).fill(fields.city)
    }
}
