package net.blueshell.api.contact.domain

import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.BrevoContactAdapter
import net.blueshell.api.contact.domain.BrevoListAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Live tests against the real Brevo API, under the `brevo-live` profile that deactivates the
 * mock adapter and activates the real one. Credentials come from the environment as in
 * production, so run it where the env file is loaded:
 *
 *   docker compose run api ./gradlew :api:test --tests "*.BrevoContactAdapterLiveIT"
 *
 * Ordered — each test builds on the state the previous one left — and teardown deletes the
 * created contact whatever the outcome.
 */
@Tag("brevo-live")
@SpringBootTest
@ActiveProfiles("brevo-live")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrevoContactAdapterLiveIT {

    @Autowired
    private lateinit var contactAdapter: BrevoContactAdapter

    @Autowired
    private lateinit var listAdapter: BrevoListAdapter

    private val testEmail = "live-test-${System.currentTimeMillis()}@esa-blueshell.nl"
    private var contactId: Long? = null

    @AfterAll
    fun teardown() {
        contactId?.let { runCatching { contactAdapter.deleteContact(it) } }
    }

    private fun contactData(firstName: String = "LiveTest") = ContactData(
        email = testEmail,
        firstName = firstName,
        lastName = "Integration",
        phoneNumber = null,
        newsletter = false,
        isMember = false
    )

    @Test
    @Order(1)
    fun `create contact succeeds`() {
        contactId = contactAdapter.createContact(contactData())
        assertThat(contactId).isGreaterThan(0)
    }

    @Test
    @Order(2)
    fun `update contact succeeds for existing contact`() {
        assumeContactExists()
        contactAdapter.updateContact(contactId!!, contactData("Updated"))
    }

    @Test
    @Order(3)
    fun `create list and add then remove contact`() {
        assumeContactExists()
        val listId = listAdapter.createList("live-test-${System.currentTimeMillis()}", "contributionPeriods")
        assertThat(listId).isGreaterThan(0)

        listAdapter.addToList(contactId!!, listId)
        listAdapter.removeFromList(contactId!!, listId)
        // Note: Brevo lists are not deleted via this adapter; the test list remains in the account.
    }

    @Test
    @Order(4)
    fun `delete contact succeeds`() {
        assumeContactExists()
        contactAdapter.deleteContact(contactId!!)
        contactId = null // prevent teardown from attempting a second delete
    }

    private fun assumeContactExists() {
        Assumptions.assumeTrue(contactId != null, "Skipped: contact from test @Order(1) is required")
    }
}
