package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object UserFormHelper {
    data class Fields(
        val initials: String? = null,
        val firstName: String? = null,
        val surname: String? = null,
        val username: String? = null,
        val discord: String? = null,
        val email: String? = null,
        val phoneNumber: String? = null,
        val password: String? = null,
        val repeatedPassword: String? = null,
        val dateOfBirth: String? = null,
        val gender: String? = null,
        val studentNumber: String? = null
    )

    fun fill(page: Page, fields: Fields) {
        fields.initials?.let { page.getByLabel("Initials*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.firstName?.let { page.getByLabel("First Name*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.surname?.let { page.getByLabel("Surname*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.username?.let { page.getByLabel("Username*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.discord?.let { page.getByLabel("Discord*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.email?.let { page.getByLabel("E-mail*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.phoneNumber?.let { page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.password?.let { page.getByLabel("Password*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.repeatedPassword?.let {
            page.getByLabel("Password (repeated)", Page.GetByLabelOptions().setExact(true)).fill(it)
        }
        fields.dateOfBirth?.let { page.getByLabel("Date of Birth*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.gender?.let { page.getByLabel("Gender*", Page.GetByLabelOptions().setExact(true)).fill(it) }
        fields.studentNumber?.let { page.getByLabel("Student Number*", Page.GetByLabelOptions().setExact(true)).fill(it) }
    }
}
