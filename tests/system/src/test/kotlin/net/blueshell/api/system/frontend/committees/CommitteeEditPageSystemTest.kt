package net.blueshell.api.system.frontend.committees

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.CommitteeEditHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollFor
import net.blueshell.systemtests.pollForValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CommitteeEditPageSystemTest : PlaywrightTestBase() {
    private fun signInAsBoard() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        assertThat(AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)).isEqualTo(200)
    }

    @Test
    fun `adds a committee with somebody on it`() {
        val suffix = TestHelper.uniqueSuffix()
        val member = TestHelper.registerActivateAndPromote(role = "MEMBER", firstName = "Create$suffix", lastName = "Member")
        val memberId = TestHelper.findUser(member.username)!!.id
        val committeeName = "SiteCie$suffix"
        signInAsBoard()

        CommitteeEditHelper.openNew(page, frontendUrl)
        CommitteeEditHelper.fillCommittee(page, committeeName, "Committee focused on testing the edit page end to end.")
        CommitteeEditHelper.addMember(page, memberId, member.fullName, "Chair")
        CommitteeEditHelper.save(page)

        val added = pollForValue("committee '$committeeName'") { TestHelper.findCommitteeByName(committeeName) }
        assertThat(added.description).contains("testing the edit page")
        pollFor("the member seated as Chair") {
            TestHelper.findCommitteeMembers(added.id).any { it.userId == memberId && it.role == "Chair" }
        }
    }

    @Test
    fun `an admin deletes a committee from its edit page`() {
        val committeeName = "DeleteCommittee${TestHelper.uniqueSuffix()}"
        val committeeId = TestHelper.createCommittee(name = committeeName, description = "Committee that will be deleted")
        val admin = TestHelper.registerActivateAndPromote("BOARD")
        TestHelper.replaceRoles(admin.username, setOf("MEMBER", "BOARD", "ADMIN"))
        assertThat(AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)).isEqualTo(200)

        CommitteeEditHelper.openEdit(page, frontendUrl, TestHelper.committeeAddressOf(committeeName))
        CommitteeEditHelper.delete(page)

        pollFor("committee $committeeId deleted") { TestHelper.findCommittee(committeeId) == null }
    }

    @Test
    fun `takes a member off and puts another on, and their committee role follows`() {
        val suffix = TestHelper.uniqueSuffix()
        val removed = TestHelper.registerActivateAndPromote(role = "MEMBER", firstName = "Removed$suffix", lastName = "Member")
        TestHelper.replaceRoles(removed.username, setOf("MEMBER", "COMMITTEE"))
        val added = TestHelper.registerActivateAndPromote(role = "MEMBER", firstName = "Added$suffix", lastName = "Member")
        val committeeName = "RoleSyncCommittee$suffix"
        val committeeId = TestHelper.createCommittee(name = committeeName, description = "Committee whose seats change")
        TestHelper.addCommitteeMember(committeeId, removed.username, role = "Chair")
        val removedId = TestHelper.findUser(removed.username)!!.id
        val addedId = TestHelper.findUser(added.username)!!.id
        signInAsBoard()

        CommitteeEditHelper.openEdit(page, frontendUrl, TestHelper.committeeAddressOf(committeeName))
        CommitteeEditHelper.removeMember(page, removedId)
        CommitteeEditHelper.addMember(page, addedId, added.fullName, "Secretary")
        CommitteeEditHelper.save(page)

        pollFor("committee membership updated") {
            val members = TestHelper.findCommitteeMembers(committeeId)
            members.size == 1 && members.first().userId == addedId && members.first().role == "Secretary"
        }
        pollFor("committee roles synced") {
            "COMMITTEE" !in TestHelper.findRoles(removed.username) && "COMMITTEE" in TestHelper.findRoles(added.username)
        }
    }

    @Test
    fun `saves a committee with nobody left on it`() {
        val suffix = TestHelper.uniqueSuffix()
        val member = TestHelper.registerActivateAndPromote(role = "MEMBER", firstName = "Leaving$suffix", lastName = "Member")
        val committeeName = "EmptiedCommittee$suffix"
        val committeeId = TestHelper.createCommittee(name = committeeName, description = "Committee everybody leaves")
        TestHelper.addCommitteeMember(committeeId, member.username, role = "Chair")
        signInAsBoard()

        CommitteeEditHelper.openEdit(page, frontendUrl, TestHelper.committeeAddressOf(committeeName))
        CommitteeEditHelper.removeMember(page, TestHelper.findUser(member.username)!!.id)
        CommitteeEditHelper.save(page)

        pollFor("committee emptied") { TestHelper.findCommitteeMembers(committeeId).isEmpty() }
    }

    @Test
    fun `renames a committee and rewrites its description`() {
        val suffix = TestHelper.uniqueSuffix()
        val committeeName = "MetaCommittee$suffix"
        val committeeId = TestHelper.createCommittee(name = committeeName, description = "Old description")
        val updatedName = "MetaCommitteeUpdated$suffix"
        val updatedDescription = "Updated description from the committee's edit page."
        signInAsBoard()

        CommitteeEditHelper.openEdit(page, frontendUrl, TestHelper.committeeAddressOf(committeeName))
        CommitteeEditHelper.fillCommittee(page, updatedName, updatedDescription)
        CommitteeEditHelper.save(page)

        pollFor("committee renamed and rewritten") {
            val now = TestHelper.findCommittee(committeeId)
            now != null && now.name == updatedName && now.description == updatedDescription
        }
    }
}
