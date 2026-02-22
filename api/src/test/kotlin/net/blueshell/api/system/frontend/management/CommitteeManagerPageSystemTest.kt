package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.domain.committee.persistence.repository.CommitteeRepository
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.CommitteeFormHelper
import net.blueshell.api.system.frontend.helper.CommitteeManagerHelper
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
            CommitteeManagerHelper.open(page, frontendUrl)
            CommitteeManagerHelper.openCreateForm(page)

            CommitteeFormHelper.fillCommittee(
                page,
                committeeName,
                "Committee focused on testing management flows end-to-end."
            )
            CommitteeFormHelper.addMember(page, role = "Chair", fullName = member.fullName)

            val response = page.waitForResponse("**/committees") {
                CommitteeFormHelper.submit(page)
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
            CommitteeManagerHelper.open(page, frontendUrl)
            waitFor(
                onTimeoutMessage = { "Expected committee '${committee.name}' to be visible before deletion" }
            ) {
                CommitteeManagerHelper.committeeRow(page, committeeId).count() > 0
            }

            CommitteeManagerHelper.openDeleteDialog(page, committeeId)

            val response = page.waitForResponse("**/committees/$committeeId") {
                page.locator("[data-testid='deletion-confirmation-confirm-btn']").first().click()
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
            CommitteeManagerHelper.open(page, frontendUrl)
            waitFor(
                onTimeoutMessage = { "Expected committee '${committee.name}' to be visible before editing" }
            ) {
                CommitteeManagerHelper.committeeRow(page, committeeId).count() > 0
            }

            CommitteeManagerHelper.openEditForm(page, committeeId)

            CommitteeFormHelper.removeFirstMember(page)
            waitFor(
                onTimeoutMessage = { "Expected existing committee member row to be removed before adding replacement" }
            ) {
                page.locator("[data-testid^='committee-form-remove-member-btn-']").count() == 0
            }

            CommitteeFormHelper.addMember(page, role = "Secretary", fullName = addedMember.fullName)

            val response = page.waitForResponse("**/committees/$committeeId") {
                CommitteeFormHelper.submit(page)
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

    @Test
    fun `updates committee name and description`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val committee = committeeFactory.create(
            name = "MetaCommittee$suffix",
            description = "Old description for metadata update test"
        )
        committeeFactory.createMember(committee = committee, user = member, role = "Chair")
        val committeeId = checkNotNull(committee.id) { "Expected persisted committee id" }
        val updatedName = "MetaCommitteeUpdated$suffix"
        val updatedDescription = "Updated description for committee manager metadata flow."

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)
            CommitteeManagerHelper.open(page, frontendUrl)

            waitFor(
                onTimeoutMessage = { "Expected committee '${committee.name}' before metadata update" }
            ) {
                CommitteeManagerHelper.committeeRow(page, committeeId).count() > 0
            }

            CommitteeManagerHelper.openEditForm(page, committeeId)
            CommitteeFormHelper.fillCommittee(page, updatedName, updatedDescription)

            val response = page.waitForResponse("**/committees/$committeeId") {
                CommitteeFormHelper.submit(page)
            }
            assertThat(response.status())
                .withFailMessage("Expected committee metadata update to succeed but got %s, body=%s", response.status(), response.text())
                .isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = { "Expected committee metadata for id=$committeeId to be updated" }
        ) {
            val refreshed = committeeRepository.findById(committeeId).orElse(null)
            refreshed != null && refreshed.name == updatedName && refreshed.description == updatedDescription
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
