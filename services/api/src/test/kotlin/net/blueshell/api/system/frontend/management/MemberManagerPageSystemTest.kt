package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.ContributionPeriodHelper
import net.blueshell.api.system.frontend.helper.MemberManagerHelper
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

    @Autowired
    private lateinit var contactRepository: ContactRepository

    @Test
    fun `member visibility follows membership period when switching periods`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val stableMember = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val periodOnlyMember = userFactory.createUserWithRole(Role.MEMBER, enabled = true)

        val uniqueOffset = System.currentTimeMillis() % 10_000
        val initialStartDate = LocalDate.now().minusDays(360L + uniqueOffset)
        val initialEndDate = initialStartDate.plusDays(30)
        val addedStartDate = LocalDate.now().plusDays(360L + uniqueOffset)
        val addedEndDate = addedStartDate.plusDays(30)

        memberRepository.saveAndFlush(
            userFactory.buildMembership(stableMember).apply {
                startDate = initialStartDate.minusDays(10)
                endDate = null
            }
        )
        memberRepository.saveAndFlush(
            userFactory.buildMembership(periodOnlyMember).apply {
                startDate = initialStartDate.minusDays(10)
                endDate = initialEndDate.minusDays(1)
            }
        )
        contributionFactory.createPeriod(initialStartDate, initialEndDate)

        val initialLabel = ContributionPeriodHelper.label(initialStartDate, initialEndDate)
        val addedLabel = ContributionPeriodHelper.label(addedStartDate, addedEndDate)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)

            waitFor(
                onTimeoutMessage = { "Expected contribution period '$initialLabel' to be visible in member manager" }
            ) {
                page.getByText(initialLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            val addStatus = ContributionPeriodHelper.createPeriod(page, addedStartDate, addedEndDate)
            assertThat(addStatus).isEqualTo(201)
            waitFor(
                onTimeoutMessage = { "Expected contribution period '$addedLabel' to be visible in member manager" }
            ) {
                page.getByText(addedLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }

            selectPeriod(page, initialLabel)
            MemberManagerHelper.openMembers(page)

            MemberManagerHelper.searchMembers(page, stableMember.username)
            waitFor(
                onTimeoutMessage = { "Expected stable member ${stableMember.username} in initial period members list" }
            ) {
                page.getByText(stableMember.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            MemberManagerHelper.searchMembers(page, periodOnlyMember.username)
            waitFor(
                onTimeoutMessage = { "Expected period-only member ${periodOnlyMember.username} in initial period members list" }
            ) {
                page.getByText(periodOnlyMember.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            selectPeriod(page, addedLabel)
            MemberManagerHelper.openMembers(page)

            MemberManagerHelper.searchMembers(page, stableMember.username)
            waitFor(
                onTimeoutMessage = { "Expected stable member ${stableMember.username} in added period members list" }
            ) {
                page.getByText(stableMember.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            MemberManagerHelper.searchMembers(page, periodOnlyMember.username)
            waitFor(
                onTimeoutMessage = { "Expected period-only member ${periodOnlyMember.username} to be absent in added period members list" }
            ) {
                page.getByText("No users found.", Page.GetByTextOptions().setExact(true)).count() > 0
            }
        }
    }

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
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)
            selectPeriod(page, periodLabel)

            MemberManagerHelper.openNonMembers(page)
            MemberManagerHelper.clickAddUser(page)

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
                page.locator("[data-testid='user-form-submit-btn']").first().click()
            }
            assertThat(createResponse.status()).isEqualTo(201)

            waitFor(
                onTimeoutMessage = { "Expected board-created user '$username' to appear in non-members list" }
            ) {
                page.getByText(username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            waitFor(
                onTimeoutMessage = { "Expected board-created user '$username' contact to be created by async contact sync" }
            ) {
                val userId = userRepository.findByUsername(username).orElse(null)?.id
                userId != null && contactRepository.findByUserId(userId) != null
            }

            MemberManagerHelper.open(page, frontendUrl)
            selectPeriod(page, periodLabel)
            MemberManagerHelper.openNonMembers(page)

            MemberManagerHelper.searchNonMembers(page, username)
            val createdUserId = waitForOptional(
                producer = { userRepository.findByUsername(username) },
                onTimeoutMessage = { "Expected board-created user '$username' to exist before update flow" }
            ).id!!
            MemberManagerHelper.clickUserRow(page, createdUserId)
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
                page.locator("[data-testid='user-form-submit-btn']").first().click()
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
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)
            selectPeriod(page, periodLabel)

            MemberManagerHelper.openNonMembers(page)

            waitFor(
                onTimeoutMessage = { "Expected non-member ${guest.username} to be visible" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            MemberManagerHelper.searchNonMembers(page, guest.username)

            val response = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "POST" &&
                        response.url().contains("/users/$guestId/memberships")
                }
            ) {
                MemberManagerHelper.clickStartMembership(page, guestId)
                page.locator("[data-testid='start-membership-confirm-btn']").first().click()
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
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)
            selectPeriod(page, periodLabel)

            MemberManagerHelper.openMembers(page)

            waitFor(
                onTimeoutMessage = { "Expected member ${member.username} to be visible in members list" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            MemberManagerHelper.searchMembers(page, member.username)

            val endResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "PUT" &&
                        response.url().contains("/memberships/$membershipId")
                }
            ) {
                MemberManagerHelper.clickEndMembership(page, member.id!!)
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

    @Test
    fun `deleted user stays visible in member manager as anonymized row`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val target = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val targetId = checkNotNull(target.id) { "Expected target id" }
        val periodLabel = createFuturePeriodLabel()

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)
            selectPeriod(page, periodLabel)
            MemberManagerHelper.openNonMembers(page)
            MemberManagerHelper.searchNonMembers(page, target.username)

            waitFor(
                onTimeoutMessage = { "Expected target user ${target.username} to be visible before deletion" }
            ) {
                page.locator("[data-testid='member-user-row-$targetId']").count() > 0
            }

            val deleteResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "DELETE" &&
                        response.url().contains("/users/$targetId")
                }
            ) {
                MemberManagerHelper.clickDeleteUser(page, targetId)
                MemberManagerHelper.confirmDelete(page)
            }
            assertThat(deleteResponse.status()).isEqualTo(204)

            MemberManagerHelper.open(page, frontendUrl)
            selectPeriod(page, periodLabel)
            MemberManagerHelper.openNonMembers(page)

            waitFor(
                onTimeoutMessage = {
                    "Expected deleted user $targetId to remain visible in member manager as anonymized row"
                }
            ) {
                page.locator("[data-testid='member-user-row-$targetId']").count() > 0
            }
        }
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
