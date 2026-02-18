package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.UserFormHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.function.Predicate

@Tag("system")
class MemberManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var contributionFactory: ContributionFactory

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `board creates member, updates fields, and sends activation mail`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val periodLabel = createFuturePeriodLabel()
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "member$suffix"
        val email = "member$suffix@test.com"
        val discord = "member$suffix"
        val phone = "+3161${suffix.takeLast(7)}"
        val updatedFirstName = "Updated"
        val updatedDiscord = "updated$suffix"

        withPage { page ->
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/members/manage")
            page.waitForURL("**/members/manage**")
            selectPeriod(page, periodLabel)

            page.getByText("Non-members", Page.GetByTextOptions().setExact(true)).click()
            page.getByText("Add User", Page.GetByTextOptions().setExact(true)).click()

            UserFormHelper.fill(
                page = page,
                fields = UserFormHelper.Fields(
                    initials = "BM",
                    firstName = "Board",
                    surname = "Member",
                    username = username,
                    discord = discord,
                    email = email,
                    phoneNumber = phone,
                    dateOfBirth = "1999-04-12",
                    gender = "X",
                    studentNumber = "s$username"
                )
            )

            val createResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "POST" &&
                        response.url().contains("/users")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Submit").setExact(true)
                ).click()
            }
            assertThat(createResponse.status()).isEqualTo(201)

            waitFor(
                onTimeoutMessage = { "Expected board-created user '$username' to appear in non-members list" }
            ) {
                page.getByText(username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            waitFor(
                onTimeoutMessage = { "Expected board-created user '$username' version to be updated by async contact sync" }
            ) {
                (userRepository.findByUsername(username).orElse(null)?.version ?: 0L) > 0L
            }

            page.navigate("$frontendUrl/members/manage")
            page.waitForURL("**/members/manage**")
            selectPeriod(page, periodLabel)
            page.getByText("Non-members", Page.GetByTextOptions().setExact(true)).click()

            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Search for a user").setExact(false)
            ).first().fill(username)
            page.getByText(username, Page.GetByTextOptions().setExact(true)).first().click()
            UserFormHelper.fill(
                page = page,
                fields = UserFormHelper.Fields(
                    firstName = updatedFirstName,
                    discord = updatedDiscord
                )
            )

            val updateResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "PUT" &&
                        response.url().contains("/users/")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Submit").setExact(true)
                ).click()
            }
            assertThat(updateResponse.status())
                .withFailMessage(
                    "Expected board update to succeed but got %s, body=%s",
                    updateResponse.status(),
                    updateResponse.text()
                )
                .isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = { "Expected board-created user '$username' to be persisted and updated" }
        ) {
            val saved = userRepository.findByUsername(username).orElse(null)
            saved?.discord == updatedDiscord && saved.firstName == updatedFirstName
        }
        assertEmailSent(email, "Activate your Account")
    }

    @Test
    fun `board starts membership for non-member`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val guest = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val periodLabel = createFuturePeriodLabel()
        val guestId = checkNotNull(guest.id) { "Expected guest id" }

        withPage { page ->
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/members/manage")
            page.waitForURL("**/members/manage**")
            selectPeriod(page, periodLabel)

            page.getByText("Non-members", Page.GetByTextOptions().setExact(true)).click()

            waitFor(
                onTimeoutMessage = { "Expected non-member ${guest.username} to be visible" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Search for a user").setExact(false)
            ).first().fill(guest.username)

            val response = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "POST" &&
                        response.url().contains("/users/$guestId/memberships")
                }
            ) {
                page.getByText("Start Membership", Page.GetByTextOptions().setExact(false)).first().click()
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Confirm").setExact(true)
                ).click()
            }
            assertThat(response.status()).isEqualTo(201)
        }

        waitFor(
            onTimeoutMessage = { "Expected active membership for user $guestId" }
        ) {
            memberRepository.existsByUser_IdAndEndDateIsNull(guestId)
        }
    }

    @Test
    fun `board ends membership`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val membership = userFactory.createMembership(member)
        val membershipId = checkNotNull(membership.id) { "Expected persisted membership id" }
        val periodLabel = createFuturePeriodLabel()

        withPage { page ->
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/members/manage")
            page.waitForURL("**/members/manage**")
            selectPeriod(page, periodLabel)

            page.getByText("Members", Page.GetByTextOptions().setExact(true)).click()

            waitFor(
                onTimeoutMessage = { "Expected member ${member.username} to be visible in members list" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Search for a user").setExact(false)
            ).first().fill(member.username)

            val endResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "PUT" &&
                        response.url().contains("/memberships/$membershipId")
                }
            ) {
                page.getByText("End Membership", Page.GetByTextOptions().setExact(false)).first().click()
            }
            assertThat(endResponse.status()).isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = { "Expected membership $membershipId to have an end date after ending membership" }
        ) {
            val current = memberRepository.findById(membershipId).orElse(null)
            current != null && current.endDate != null
        }
    }

    private fun loginThroughUi(page: Page, username: String, password: String): Int {
        page.navigate("$frontendUrl/login/")
        page.getByLabel("Username").fill(username)
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Password")
        ).fill(password)

        val response = page.waitForResponse("**/auth") {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()
        }
        return response.status()
    }

    private fun createFuturePeriodLabel(): String {
        val uniqueOffset = System.currentTimeMillis() % 1_000
        val startDate = LocalDate.now().plusDays(900L + uniqueOffset)
        val endDate = startDate.plusDays(60)
        contributionFactory.createPeriod(startDate, endDate)
        return "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"
    }

    private fun selectPeriod(page: Page, periodLabel: String) {
        waitFor(
            onTimeoutMessage = { "Expected contribution period '$periodLabel' to be visible in member manager" }
        ) {
            page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
        }
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
