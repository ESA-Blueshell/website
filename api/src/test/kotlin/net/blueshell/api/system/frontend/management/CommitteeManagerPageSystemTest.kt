package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.committee.persistence.repository.CommitteeRepository
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class CommitteeManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var committeeRepository: CommitteeRepository

    @Test
    fun `creates committee from manager`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val committeeName = "SiteCie$suffix"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)
            openCommitteeManager(page)
            page.getByText("Create new committee", Page.GetByTextOptions().setExact(false)).first().click()

            page.getByLabel("Committee name").fill(committeeName)
            page.getByLabel("Description").fill("Committee focused on testing management flows end-to-end.")
            page.getByLabel("Role").fill("Chair")
            page.getByRole(
                AriaRole.COMBOBOX,
                Page.GetByRoleOptions().setName("Member name").setExact(false)
            ).first().fill(member.fullName)
            page.getByRole(
                AriaRole.COMBOBOX,
                Page.GetByRoleOptions().setName("Member name").setExact(false)
            ).first().press("Enter")

            val response = page.waitForResponse("**/committees") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Submit").setExact(false)
                ).click()
            }
            assertThat(response.status()).isEqualTo(201)
        }

        waitFor(
            onTimeoutMessage = { "Expected committee '$committeeName' to be persisted" }
        ) {
            committeeRepository.findAll().any { it.name == committeeName }
        }
        val persisted = committeeRepository.findAll().first { it.name == committeeName }
        assertThat(persisted.description).contains("testing management flows")
        assertThat(persisted.members.mapNotNull { it.user.id }).contains(member.id)
    }

    @Test
    fun `deletes committee from manager`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val committee = committeeFactory.create(
            name = "DeleteCommittee${System.currentTimeMillis().toString().takeLast(6)}",
            description = "Committee that will be deleted through board management page"
        )
        committeeFactory.createMember(committee = committee, user = member, role = "Chair")
        val committeeId = checkNotNull(committee.id) { "Expected persisted committee id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)
            openCommitteeManager(page)
            waitFor(
                onTimeoutMessage = { "Expected committee '${committee.name}' to be visible before deletion" }
            ) {
                page.getByText(committee.name, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            page.locator("button:has(i.mdi-delete)").first().click()

            val response = page.waitForResponse("**/committees/$committeeId") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Delete").setExact(true)
                ).click()
            }
            assertThat(response.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected committee $committeeId to be deleted" }
        ) {
            committeeRepository.findById(committeeId).isEmpty
        }
    }

    @Test
    fun `updates committee members and committee roles`() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val removedMember = userFactory.createUserWithRole(Role.MEMBER, enabled = true).apply {
            firstName = "Removed$suffix"
            lastName = "Member"
            addRole(Role.COMMITTEE)
        }
        userRepository.save(removedMember)
        val addedMember = userFactory.createUserWithRole(Role.MEMBER, enabled = true).apply {
            firstName = "Added$suffix"
            lastName = "Member"
        }
        userRepository.save(addedMember)
        val committee = committeeFactory.create(
            name = "RoleSyncCommittee${System.currentTimeMillis().toString().takeLast(6)}",
            description = "Committee used to verify role sync after member changes"
        )
        committeeFactory.createMember(committee = committee, user = removedMember, role = "Chair")
        val committeeId = checkNotNull(committee.id) { "Expected persisted committee id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)
            openCommitteeManager(page)
            waitFor(
                onTimeoutMessage = { "Expected committee '${committee.name}' to be visible before editing" }
            ) {
                page.getByText(committee.name, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            page.locator("button:has(i.mdi-pencil)").first().click()

            page.locator(".my-3 button:has(i.mdi-close)").first().click()
            waitFor(
                onTimeoutMessage = { "Expected existing committee member row to be removed before adding replacement" }
            ) {
                page.getByRole(
                    AriaRole.COMBOBOX,
                    Page.GetByRoleOptions().setName("Member name").setExact(false)
                ).count() == 0
            }

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Add member").setExact(false)
            ).first().click()

            page.getByLabel("Role").first().fill("Secretary")
            page.getByRole(
                AriaRole.COMBOBOX,
                Page.GetByRoleOptions().setName("Member name").setExact(false)
            ).first().fill(addedMember.fullName)
            page.getByRole(
                AriaRole.COMBOBOX,
                Page.GetByRoleOptions().setName("Member name").setExact(false)
            ).first().press("Enter")

            val response = page.waitForResponse("**/committees/$committeeId") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Submit").setExact(false)
                ).click()
            }
            assertThat(response.status())
                .withFailMessage("Expected update to succeed but got %s, body=%s", response.status(), response.text())
                .isEqualTo(200)
        }

        waitFor(
            timeoutMs = 12_000,
            onTimeoutMessage = {
                val refreshed = committeeRepository.findById(committeeId).orElse(null)
                "Expected committee membership to be updated to only the replacement member, current members=${
                    refreshed?.members?.map { it.userId to it.role }
                }"
            }
        ) {
            val refreshed = committeeRepository.findById(committeeId).orElse(null)
            refreshed != null &&
                refreshed.members.size == 1 &&
                refreshed.members.first().userId == checkNotNull(addedMember.id)
        }

        waitFor(
            timeoutMs = 12_000,
            onTimeoutMessage = { "Expected committee roles to be synchronized after membership update" }
        ) {
            val removed = userRepository.findById(checkNotNull(removedMember.id)).orElse(null)
            val added = userRepository.findById(checkNotNull(addedMember.id)).orElse(null)
            removed != null &&
                added != null &&
                !removed.roles.contains(Role.COMMITTEE) &&
                added.roles.contains(Role.COMMITTEE)
        }

        val removedAfter = waitForOptional(
            producer = { userRepository.findById(checkNotNull(removedMember.id)) },
            onTimeoutMessage = { "Expected removed user to be present after committee update" }
        )
        val addedAfter = waitForOptional(
            producer = { userRepository.findById(checkNotNull(addedMember.id)) },
            onTimeoutMessage = { "Expected added user to be present after committee update" }
        )
        assertThat(removedAfter.roles).doesNotContain(Role.COMMITTEE)
        assertThat(addedAfter.roles).contains(Role.COMMITTEE)
    }

    private fun openCommitteeManager(page: Page) {
        page.navigate("$frontendUrl/committees/manage")
        page.waitForURL("**/committees/manage**")
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
