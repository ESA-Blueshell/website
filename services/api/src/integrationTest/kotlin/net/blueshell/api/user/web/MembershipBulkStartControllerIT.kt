package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Starting membership for a selection. As with ending, the last test plays one selection
 * through both endpoints and holds their verdicts against each other, because preview and
 * apply are supposed to be the same decision read twice.
 */
@SpringBootTest
class MembershipBulkStartControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var membershipRepository: MemberRepository

    private fun body(userIds: List<Long?>) = """{"userIds":[${userIds.joinToString(",")}]}"""

    @Test
    fun `starting membership for members who have none`() {
        val board = createUserWithRole(Role.BOARD)
        val newcomer = createUserWithRole(Role.MEMBER)

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(newcomer.id))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(1))
            .andExpect(jsonPath("$.skipped").value(0))

        val started = membershipRepository.findByUser_Id(newcomer.id!!).single()
        assertThat(started.startDate).isEqualTo(LocalDate.now())
        assertThat(started.endDate).isNull()
        assertThat(started.memberType).isEqualTo(MemberType.REGULAR)
    }

    @Test
    fun `a returning member gets a fresh spell rather than their old one reopened`() {
        val board = createUserWithRole(Role.BOARD)
        val returner = createUserWithRole(Role.MEMBER)
        val old = createMembershipFixture(
            user = returner,
            memberType = MemberType.ALUMNI,
            startDate = LocalDate.now().minusYears(3),
            endDate = LocalDate.now().minusYears(2),
        )

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(returner.id))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(1))

        val held = membershipRepository.findByUser_Id(returner.id!!)
        assertThat(held).hasSize(2)
        // The old spell is untouched, so "member since" still reads the day they first joined.
        assertThat(membershipRepository.findById(old.id!!).orElseThrow().endDate)
            .isEqualTo(LocalDate.now().minusYears(2))
        assertThat(held.minOf { it.startDate }).isEqualTo(LocalDate.now().minusYears(3))

        // The type carries over — a returning alumnus comes back an alumnus — but the
        // incasso mandate does not: a standing authorisation to take money, given for a
        // membership that has since ended, is not re-armed on somebody's behalf in a batch.
        val fresh = held.single { it.endDate == null }
        assertThat(fresh.startDate).isEqualTo(LocalDate.now())
        assertThat(fresh.memberType).isEqualTo(MemberType.ALUMNI)
        assertThat(fresh.incasso).isFalse()
        assertThat(membershipRepository.findById(old.id!!).orElseThrow().incasso).isTrue()
    }

    @Test
    fun `a member who is already active is skipped rather than given a second membership`() {
        val board = createUserWithRole(Role.BOARD)
        val active = createMembershipFixture()

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(active.userId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(0))
            .andExpect(jsonPath("$.skipped").value(1))

        assertThat(membershipRepository.findByUser_Id(active.userId)).hasSize(1)
    }

    @Test
    fun `the preview names every selected member and what will happen to them`() {
        val board = createUserWithRole(Role.BOARD)
        val newcomer = createUserWithRole(Role.MEMBER)
        val active = createMembershipFixture()

        mvc.perform(
            post("/memberships/bulk/start/preview")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(newcomer.id, active.userId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.effectiveDate").value(LocalDate.now().toString()))
            .andExpect(jsonPath("$.rows[0].disposition").value("INCLUDED"))
            .andExpect(jsonPath("$.rows[0].reason").value("WILL_START_NEW"))
            .andExpect(jsonPath("$.rows[1].disposition").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[1].reason").value("ALREADY_ACTIVE"))
    }

    @Test
    fun `starting membership gives the member role back`() {
        val board = createUserWithRole(Role.BOARD)
        // A former member whose role went with their membership, which is what ending one
        // leaves behind.
        val returner = createUserWithRole(Role.GUEST)
        createMembershipFixture(
            user = returner,
            startDate = LocalDate.now().minusYears(3),
            endDate = LocalDate.now().minusYears(2),
        )

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(returner.id))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(1))

        assertThat(refreshUser(returner).roles).contains(Role.MEMBER)
    }

    @Test
    fun `a selection naming an id that was never a user is refused`() {
        val board = createUserWithRole(Role.BOARD)
        val newcomer = createUserWithRole(Role.MEMBER)
        val missingId = newcomer.id!! + 999_999

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(newcomer.id, missingId))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[0].code").value("UnknownUserIds"))
            .andExpect(jsonPath("$.errors[0].values[0]").value(missingId))

        assertThat(membershipRepository.findByUser_Id(newcomer.id!!)).isEmpty()
    }

    /**
     * The property the endpoint pair exists to hold: what the preview said about each row
     * is what the apply did to it. Counts alone would pass with two verdicts swapped, so
     * this reads the preview per member and then counts that member's memberships.
     */
    @Test
    fun `the preview and the apply agree over the same selection, row by row`() {
        val board = createUserWithRole(Role.BOARD)
        val newcomer = createUserWithRole(Role.MEMBER)
        val active = createMembershipFixture()
        val returner = createMembershipFixture(endDate = LocalDate.now().minusMonths(2))
        val selection = listOf(newcomer.id, active.userId, returner.userId)

        val preview = mvc.perform(
            post("/memberships/bulk/start/preview")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val verdicts: Map<Long, String> = mapper.readTree(preview)["rows"]
            .associate { it["userId"].asLong() to it["disposition"].asString() }
        assertThat(verdicts.keys).containsExactlyInAnyOrderElementsOf(selection.filterNotNull())

        val before = selection.filterNotNull().associateWith { membershipRepository.findByUser_Id(it).size }

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(verdicts.values.count { it == "INCLUDED" }))
            .andExpect(jsonPath("$.skipped").value(verdicts.values.count { it != "INCLUDED" }))

        for ((userId, disposition) in verdicts) {
            val after = membershipRepository.findByUser_Id(userId)
            val expected = if (disposition == "INCLUDED") before.getValue(userId) + 1 else before.getValue(userId)
            assertThat(after).describedAs("member %s was previewed %s", userId, disposition).hasSize(expected)
            if (disposition == "INCLUDED") {
                assertThat(after.single { it.endDate == null }.startDate).isEqualTo(LocalDate.now())
            }
        }
    }

    @Test
    fun `a member cannot start memberships in bulk`() {
        val member = createUserWithRole(Role.MEMBER)
        val newcomer = createUserWithRole(Role.MEMBER)

        mvc.perform(
            post("/memberships/bulk/start")
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(newcomer.id))),
        )
            .andExpect(status().isForbidden)

        assertThat(membershipRepository.findByUser_Id(newcomer.id!!)).isEmpty()
    }
}
