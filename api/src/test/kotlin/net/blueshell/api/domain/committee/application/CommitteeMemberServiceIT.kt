package net.blueshell.api.domain.committee.application

import net.blueshell.api.domain.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.committee.CommitteeMemberFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.ServiceTestSupport
import net.blueshell.api.domain.user.application.UserService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class CommitteeMemberServiceIT : ServiceTestSupport() {

    @Autowired
    private lateinit var committeeMemberService: net.blueshell.api.domain.committee.application.CommitteeMemberService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var committeeMemberFactory: CommitteeMemberFactory

    @Nested
    inner class Create {

        @Test
        fun `publishes change event and assigns committee role`() {
            val user = persist(userFactory.createBasic())
            val committee = persist(committeeFactory.createBasic())
            val member = committeeMemberFactory.createBasic(user, committee)

            committeeMemberService.create(member)

            val updatedUser = userService.findById(user.id!!)
            assertTrue(updatedUser.hasRole(Role.COMMITTEE))
            assertTrue(
                applicationEvents.stream(CommitteeMembershipChanged::class.java).anyMatch { it.userId == user.id })
        }
    }

    @Nested
    inner class DeleteById {

        @Test
        fun `removes committee role when last membership is deleted`() {
            val user = persist(userFactory.createBasic())
            val committee = persist(committeeFactory.createBasic())
            val member = committeeMemberFactory.createBasic(user, committee)

            committeeMemberService.create(member)
            assertTrue(userService.findById(user.id!!).hasRole(Role.COMMITTEE))

            committeeMemberService.deleteById(
                CommitteeMember.Id(committeeId = committee.id!!, userId = user.id!!)
            )

            val updatedUser = userService.findById(user.id!!)
            assertFalse(updatedUser.hasRole(Role.COMMITTEE))
        }
    }
}
