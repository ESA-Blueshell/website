package net.blueshell.api.auth.web

import jakarta.validation.Validation
import jakarta.validation.Validator
import net.blueshell.api.user.web.UpsertMemberProfileRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The signup bodies carry their own refusals. Asserted against a real validator so a
 * dropped annotation fails here rather than as a 200 on a half-filled form.
 */
class SignupRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun messagesFor(body: Any): List<String> =
        validator.validate(body).map { it.propertyPath.toString() }

    @Test
    fun `the application refuses conditions that were not accepted`() {
        assertThat(messagesFor(SignupApplicationRequest(conditionsAccepted = false)))
            .containsExactly("conditionsAccepted")
    }

    // A trap: @AssertTrue passes a null, so an omitted field is not the same refusal as
    // an explicit false. The body is nullable to make the false reachable at all.
    @Test
    fun `the application lets an omitted acceptance through the validator`() {
        assertThat(messagesFor(SignupApplicationRequest())).isEmpty()
    }

    @Test
    fun `the application passes once the conditions are accepted`() {
        assertThat(messagesFor(SignupApplicationRequest(conditionsAccepted = true))).isEmpty()
    }

    @Test
    fun `the email correction refuses anything that is not an address`() {
        assertThat(messagesFor(SignupEmailRequest(email = "not-an-address"))).containsExactly("email")
        assertThat(messagesFor(SignupEmailRequest(email = " "))).contains("email")
        assertThat(messagesFor(SignupEmailRequest(email = "corrected@example.com"))).isEmpty()
    }

    @Test
    fun `the address refuses a blank line`() {
        val blankCity = SignupAddressRequest(
            country = "NL",
            city = " ",
            street = "Drienerlolaan",
            houseNumber = "5",
            zipCode = "7522NB",
        )

        assertThat(messagesFor(blankCity)).containsExactly("city")
    }

    @Test
    fun `the details refuse a blank username and accept an absent prefix`() {
        val details = SignupDetailsRequest(
            username = " ",
            initials = "AP",
            firstName = "App",
            lastName = "Licant",
            discord = "applicant#0001",
            phoneNumber = "0612345678",
            newsletter = true,
        )

        assertThat(messagesFor(details)).containsExactly("username")
        assertThat(details.prefix).isNull()
        assertThat(details.photoConsent).isNull()
        assertThat(details.memberProfile).isNull()
    }

    // @Valid on the nested profile is what makes the second step's fields refusable at
    // all; without it a blank nationality would travel as far as the entity.
    @Test
    fun `the details carry the refusals of the profile inside them`() {
        val details = SignupDetailsRequest(
            username = "applicant",
            initials = "AP",
            firstName = "App",
            prefix = "van",
            lastName = "Licant",
            discord = "applicant#0001",
            phoneNumber = "0612345678",
            newsletter = true,
            photoConsent = true,
            memberProfile = UpsertMemberProfileRequest(
                dateOfBirth = LocalDate.of(2000, 1, 2),
                nationality = " ",
                bhv = false,
                ehbo = false,
            ),
        )

        assertThat(messagesFor(details)).containsExactly("memberProfile.nationality")
    }
}
