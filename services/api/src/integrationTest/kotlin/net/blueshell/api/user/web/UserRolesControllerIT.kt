package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.RoleChangeRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * An admin changing what a person may reach, as the admin and the api can observe it: a status
 * code, the roles that come back, the history row and the queued notification.
 */
@SpringBootTest
class UserRolesControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var roleChanges: RoleChangeRepository

    private fun body(
        roles: List<Role>,
        note: String? = null,
    ): String = mapper.writeValueAsString(mapOf("roles" to roles.map { it.name }, "note" to note))

    private fun setRoles(
        actorId: Long,
        subjectId: Long,
        roles: List<Role>,
        note: String? = null,
    ) = mvc.perform(
        put("/users/{userId}/roles", subjectId)
            .with(bearer(userRepository.findById(actorId).orElseThrow()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(roles, note)),
    )

    /** Native: the entity hides a soft-deleted row from every query Hibernate writes. */
    private fun softDelete(userId: Long) {
        transactionTemplate.execute {
            entityManager
                .createNativeQuery("UPDATE users SET deleted_at = NOW() WHERE id = :id")
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    private fun grantAdminToServiceAccount() {
        transactionTemplate.execute {
            entityManager
                .createNativeQuery(
                    """
                INSERT INTO authorities (user_id, authority)
                SELECT a.user_id, 'ADMIN' FROM authorities a WHERE a.authority = 'SYSTEM'
                """,
                ).executeUpdate()
        }
    }

    @Nested
    inner class Granting {
        @Test
        fun `an admin grants board, and the person holds it`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.granted[0]").value(Role.BOARD.name))

            assertThat(refreshUser(subject).roles).contains(Role.BOARD)
        }

        @Test
        fun `the request states the whole set, so a role left out is revoked`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)
            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD, Role.TREASURER)).andExpect(status().isOk)

            setRoles(admin.id!!, subject.id!!, listOf(Role.TREASURER)).andExpect(status().isOk)

            assertThat(refreshUser(subject).roles).contains(Role.TREASURER).doesNotContain(Role.BOARD)
        }

        @Test
        fun `a derived role survives a grant that does not name it`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            assertThat(refreshUser(subject).roles).contains(Role.MEMBER)
        }

        @Test
        fun `a repeated identical request changes nothing and writes no second record`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)
            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            assertThat(roleChanges.findBySubjectNewestFirst(subject.id!!)).hasSize(1)
        }

        @Test
        fun `an admin may lower their own privileges`() {
            val admin = createUserWithRole(Role.ADMIN)
            createUserWithRole(Role.ADMIN)

            setRoles(admin.id!!, admin.id!!, emptyList()).andExpect(status().isOk)

            assertThat(refreshUser(admin).roles).doesNotContain(Role.ADMIN)
        }
    }

    @Nested
    inner class Refusals {
        @Test
        fun `a role outside the assignable set is refused with its code`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.GUEST)

            setRoles(admin.id!!, subject.id!!, listOf(Role.COMMITTEE))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("RoleNotAssignable"))
                .andExpect(jsonPath("$.role").value(Role.COMMITTEE.name))
        }

        @Test
        fun `the internal role is not a choice`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.GUEST)

            setRoles(admin.id!!, subject.id!!, listOf(Role.SYSTEM))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("RoleNotAssignable"))
        }

        @Test
        fun `raising one's own privileges is refused`() {
            val admin = createUserWithRole(Role.ADMIN)

            setRoles(admin.id!!, admin.id!!, listOf(Role.ADMIN, Role.TREASURER))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `removing the last admin is refused, and says which rule refused it`() {
            val admin = createUserWithRole(Role.ADMIN)

            setRoles(admin.id!!, admin.id!!, emptyList())
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("LastAdministrator"))

            assertThat(refreshUser(admin).roles).contains(Role.ADMIN)
        }

        @Test
        fun `a soft-deleted admin is not a way back in, so it does not count`() {
            val admin = createUserWithRole(Role.ADMIN)
            val second = createUserWithRole(Role.ADMIN)
            softDelete(second.id!!)

            setRoles(admin.id!!, admin.id!!, emptyList())
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("LastAdministrator"))
        }

        @Test
        fun `the service account does not count towards the tally`() {
            // Nobody signs in as it, so an admin stepping down while it is the only other
            // holder would leave the association with no way in.
            val admin = createUserWithRole(Role.ADMIN)
            grantAdminToServiceAccount()

            setRoles(admin.id!!, admin.id!!, emptyList())
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("LastAdministrator"))
        }
    }

    @Nested
    inner class Standing {
        @Test
        fun `the panel names each role's source and the set an admin may grant`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)
            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            mvc
                .perform(get("/users/{userId}/roles", subject.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.granted[0]").value(Role.BOARD.name))
                .andExpect(jsonPath("$.derived[0].role").value(Role.MEMBER.name))
                .andExpect(jsonPath("$.derived[0].source").value("MEMBERSHIP"))
                .andExpect(jsonPath("$.assignable").value(contains("BOARD", "TREASURER", "ADMIN")))
        }

        @Test
        fun `the role every account carries names the account as its source`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.GUEST)

            mvc
                .perform(get("/users/{userId}/roles", subject.id).with(bearer(admin)))
                .andExpect(jsonPath("$.derived[0].role").value(Role.GUEST.name))
                .andExpect(jsonPath("$.derived[0].source").value("ACCOUNT"))
        }

        @Test
        fun `a role held only by inheritance reads as implied`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)
            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            mvc
                .perform(get("/users/{userId}/roles", subject.id).with(bearer(admin)))
                .andExpect(jsonPath("$.implied[0]").value(Role.COMMITTEE.name))
        }
    }

    @Nested
    inner class History {
        @Test
        fun `a saved change is in the history at once, with actor, note and both sides`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD), "Took office today")
                .andExpect(status().isOk)

            mvc
                .perform(get("/users/{userId}/role-changes", subject.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].actorId").value(admin.id))
                .andExpect(jsonPath("$[0].note").value("Took office today"))
                .andExpect(jsonPath("$[0].before[0]").value(Role.MEMBER.name))
                .andExpect(jsonPath("$[0].after").value(contains("MEMBER", "BOARD")))
        }

        @Test
        fun `a change without a note is recorded without one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            mvc
                .perform(get("/users/{userId}/role-changes", subject.id).with(bearer(admin)))
                .andExpect(jsonPath("$[0].note").doesNotExist())
        }

        @Test
        fun `newest first`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)
            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD), "in").andExpect(status().isOk)

            setRoles(admin.id!!, subject.id!!, emptyList(), "out").andExpect(status().isOk)

            mvc
                .perform(get("/users/{userId}/role-changes", subject.id).with(bearer(admin)))
                .andExpect(jsonPath("$[0].note").value("out"))
                .andExpect(jsonPath("$[1].note").value("in"))
        }
    }

    @Nested
    inner class Notification {
        @Test
        fun `gaining board queues the person's notification`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.MEMBER)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isOk)

            assertThat(findJobsByType(EmailJobs.RoleChange.type)).hasSize(1)
        }

        @Test
        fun `losing admin queues one too`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.ADMIN)
            setRoles(admin.id!!, subject.id!!, emptyList()).andExpect(status().isOk)

            assertThat(findJobsByType(EmailJobs.RoleChange.type)).hasSize(1)
        }

        @Test
        fun `a treasurer change goes out quietly`() {
            val admin = createUserWithRole(Role.ADMIN)
            val subject = createUserWithRole(Role.BOARD)

            setRoles(admin.id!!, subject.id!!, listOf(Role.BOARD, Role.TREASURER))
                .andExpect(status().isOk)

            assertThat(findJobsByType(EmailJobs.RoleChange.type)).isEmpty()
        }
    }

    @Nested
    inner class WhoMayAsk {
        @Test
        fun `a board member who is not an admin is refused the whole panel`() {
            val board = createUserWithRole(Role.BOARD)
            val subject = createUserWithRole(Role.MEMBER)

            mvc
                .perform(get("/users/{userId}/roles", subject.id).with(bearer(board)))
                .andExpect(status().isForbidden)
            mvc
                .perform(get("/users/{userId}/role-changes", subject.id).with(bearer(board)))
                .andExpect(status().isForbidden)
            setRoles(board.id!!, subject.id!!, listOf(Role.BOARD)).andExpect(status().isForbidden)
        }

        @Test
        fun `nobody signed in reads nothing`() {
            val subject = createUserWithRole(Role.MEMBER)

            mvc
                .perform(get("/users/{userId}/roles", subject.id))
                .andExpect(status().isUnauthorized)
        }
    }
}
