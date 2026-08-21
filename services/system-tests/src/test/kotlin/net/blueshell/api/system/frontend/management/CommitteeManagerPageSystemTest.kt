package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.CommitteeFormHelper
import net.blueshell.api.system.frontend.helper.CommitteeManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollFor
import net.blueshell.systemtests.pollForValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CommitteeManagerPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `creates committee from manager`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val memberSuffix = System.currentTimeMillis().toString().takeLast(6)
        val member = TestHelper.registerActivateAndPromote(
            role = "MEMBER",
            firstName = "Create$memberSuffix",
            lastName = "Member",
        )
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val committeeName = "SiteCie$suffix"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)
        CommitteeManagerHelper.open(page, frontendUrl)
        CommitteeManagerHelper.openCreateForm(page)

        CommitteeFormHelper.fillCommittee(
            page,
            committeeName,
            "Committee focused on testing management flows end-to-end.",
        )
        CommitteeFormHelper.addMember(page, role = "Chair", fullName = member.fullName)

        val response = page.waitForResponse("**/committees") {
            CommitteeFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(201)

        val memberId = TestHelper.findUser(member.username)!!.id
        val byName = pollForCommitteeByName(committeeName)
        assertThat(byName.description).contains("testing management flows")
        val members = TestHelper.findCommitteeMembers(byName.id)
        assertThat(members.map { it.userId }).contains(memberId)
    }

    @Test
    fun `deletes committee from manager`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val committeeName = "DeleteCommittee${System.currentTimeMillis().toString().takeLast(6)}"
        val committeeId = TestHelper.createCommittee(
            name = committeeName,
            description = "Committee that will be deleted through board management page",
        )
        TestHelper.addCommitteeMember(committeeId, member.username, role = "Chair")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)
        CommitteeManagerHelper.open(page, frontendUrl)
        CommitteeManagerHelper.committeeRow(page, committeeId).first().waitFor()

        CommitteeManagerHelper.openDeleteDialog(page, committeeId)

        val response = page.waitForResponse("**/committees/$committeeId") {
            page.locator("[data-testid='deletion-confirmation-confirm-btn']").first().click()
        }
        assertThat(response.status()).isEqualTo(204)

        pollFor("committee $committeeId deleted") { TestHelper.findCommittee(committeeId) == null }
    }

    @Test
    fun `updates committee members and committee roles`() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val removedMember = TestHelper.registerActivateAndPromote(
            role = "MEMBER",
            firstName = "Removed$suffix",
            lastName = "Member",
        )
        // Match the original test: removedMember starts with both MEMBER and COMMITTEE.
        TestHelper.replaceRoles(removedMember.username, setOf("MEMBER", "COMMITTEE"))
        val addedMember = TestHelper.registerActivateAndPromote(
            role = "MEMBER",
            firstName = "Added$suffix",
            lastName = "Member",
        )
        val committeeName = "RoleSyncCommittee${System.currentTimeMillis().toString().takeLast(6)}"
        val committeeId = TestHelper.createCommittee(
            name = committeeName,
            description = "Committee used to verify role sync after member changes",
        )
        TestHelper.addCommitteeMember(committeeId, removedMember.username, role = "Chair")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)
        CommitteeManagerHelper.open(page, frontendUrl)
        CommitteeManagerHelper.committeeRow(page, committeeId).first().waitFor()

        CommitteeManagerHelper.openEditForm(page, committeeId)

        CommitteeFormHelper.removeFirstMember(page)
        pollFor("existing member row removed") {
            page.locator("[data-testid^='committee-form-remove-member-btn-']").count() == 0
        }

        CommitteeFormHelper.addMember(page, role = "Secretary", fullName = addedMember.fullName)

        val response = page.waitForResponse("**/committees/$committeeId") {
            CommitteeFormHelper.submit(page)
        }
        assertThat(response.status())
            .withFailMessage("Expected update to succeed but got %s, body=%s", response.status(), response.text())
            .isEqualTo(200)

        val addedId = TestHelper.findUser(addedMember.username)!!.id
        pollFor("committee membership updated") {
            val members = TestHelper.findCommitteeMembers(committeeId)
            members.size == 1 && members.first().userId == addedId
        }

        pollFor("committee roles synced") {
            val removedRoles = TestHelper.findRoles(removedMember.username)
            val addedRoles = TestHelper.findRoles(addedMember.username)
            "COMMITTEE" !in removedRoles && "COMMITTEE" in addedRoles
        }

        assertThat(TestHelper.findRoles(removedMember.username)).doesNotContain("COMMITTEE")
        assertThat(TestHelper.findRoles(addedMember.username)).contains("COMMITTEE")
    }

    @Test
    fun `updates committee name and description`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val committeeName = "MetaCommittee$suffix"
        val committeeId = TestHelper.createCommittee(
            name = committeeName,
            description = "Old description for metadata update test",
        )
        TestHelper.addCommitteeMember(committeeId, member.username, role = "Chair")
        val updatedName = "MetaCommitteeUpdated$suffix"
        val updatedDescription = "Updated description for committee manager metadata flow."

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)
        CommitteeManagerHelper.open(page, frontendUrl)
        CommitteeManagerHelper.committeeRow(page, committeeId).first().waitFor()

        CommitteeManagerHelper.openEditForm(page, committeeId)
        CommitteeFormHelper.fillCommittee(page, updatedName, updatedDescription)

        val response = page.waitForResponse("**/committees/$committeeId") {
            CommitteeFormHelper.submit(page)
        }
        assertThat(response.status())
            .withFailMessage("Expected metadata update to succeed but got %s, body=%s", response.status(), response.text())
            .isEqualTo(200)

        pollFor("committee metadata updated") {
            val refreshed = TestHelper.findCommittee(committeeId)
            refreshed != null && refreshed.name == updatedName && refreshed.description == updatedDescription
        }
    }
    private fun pollForCommitteeByName(name: String): TestHelper.CommitteeRow =
        pollForValue("committee '$name'") { findCommitteeByName(name) }

    private fun findCommitteeByName(name: String): TestHelper.CommitteeRow? {
        // Inline SQL — `TestHelper` exposes by-id; this scoped lookup is
        // only useful to this test class so it stays local.
        java.sql.DriverManager.getConnection(
            System.getProperty("test.db.url", "jdbc:mariadb://localhost:3306/blueshell"),
            System.getProperty("test.db.user", "blueshell"),
            System.getProperty("test.db.password", "ci-blueshell"),
        ).use { conn ->
            conn.prepareStatement(
                "SELECT id, name, description FROM committees " +
                    "WHERE name = ? AND deleted_at = '9999-12-31 23:59:59'",
            ).use { stmt ->
                stmt.setString(1, name)
                val rs = stmt.executeQuery()
                return if (rs.next()) {
                    TestHelper.CommitteeRow(
                        id = rs.getLong("id"),
                        name = rs.getString("name"),
                        description = rs.getString("description"),
                    )
                } else {
                    null
                }
            }
        }
    }
}
