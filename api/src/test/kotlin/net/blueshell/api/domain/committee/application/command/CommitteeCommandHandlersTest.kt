package net.blueshell.api.domain.committee.application.command

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.command.CommitteeMemberData
import net.blueshell.api.domain.committee.command.CreateCommitteeCommand
import net.blueshell.api.domain.committee.command.DeleteCommitteeByIdCommand
import net.blueshell.api.domain.committee.command.FindCommitteeByIdCommand
import net.blueshell.api.domain.committee.command.FindCommitteesCommand
import net.blueshell.api.domain.committee.command.FindCommitteesForCurrentUserCommand
import net.blueshell.api.domain.committee.command.UpdateCommitteeCommand
import net.blueshell.api.domain.committee.persistence.Committee
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class CommitteeCommandHandlersTest {

    private val committeeService = mock<CommitteeService>()

    @Nested
    inner class FindCommitteesForCurrentUser {

        private val handler = FindCommitteesForCurrentUserHandler(committeeService)

        @Test
        fun `returns empty list when principal id is missing`() {
            val result = handler.handle(FindCommitteesForCurrentUserCommand(principalId = null, includeAll = false))

            assertThat(result).isEmpty()
            verifyNoInteractions(committeeService)
        }

        @Test
        fun `returns all committees when include all is true`() {
            val committees = mutableListOf(committee("A", "Desc A"), committee("B", "Desc B"))
            whenever(committeeService.findAll()).thenReturn(committees)

            val result = handler.handle(FindCommitteesForCurrentUserCommand(principalId = 7L, includeAll = true))

            assertThat(result).isSameAs(committees)
            verify(committeeService).findAll()
        }

        @Test
        fun `returns committees for principal when include all is false`() {
            val committees = mutableListOf(committee("A", "Desc A"))
            whenever(committeeService.findAllByUserId(7L)).thenReturn(committees)

            val result = handler.handle(FindCommitteesForCurrentUserCommand(principalId = 7L, includeAll = false))

            assertThat(result).isSameAs(committees)
            verify(committeeService).findAllByUserId(7L)
        }
    }

    @Nested
    inner class FindCommittees {

        private val handler = FindCommitteesHandler(committeeService)

        @Test
        fun `returns all committees`() {
            val committees = mutableListOf(committee("A", "Desc A"))
            whenever(committeeService.findAll()).thenReturn(committees)

            val result = handler.handle(FindCommitteesCommand())

            assertThat(result).isSameAs(committees)
            verify(committeeService).findAll()
        }
    }

    @Nested
    inner class FindCommitteeById {

        private val handler = FindCommitteeByIdHandler(committeeService)

        @Test
        fun `returns committee by id`() {
            val expected = committee("A", "Desc A")
            whenever(committeeService.findById(2L)).thenReturn(expected)

            val result = handler.handle(FindCommitteeByIdCommand(committeeId = 2L))

            assertThat(result).isSameAs(expected)
            verify(committeeService).findById(2L)
        }
    }

    @Nested
    inner class CreateCommittee {

        private val handler = CreateCommitteeHandler(committeeService)

        @Test
        fun `creates committee with members`() {
            val members = mutableListOf(CommitteeMemberData(1L, "Chair"))
            val expected = committee("Committee", "Description")
            whenever(
                committeeService.createWithMembers(
                    name = "Committee",
                    description = "Description",
                    members = members
                )
            ).thenReturn(expected)

            val result = handler.handle(
                CreateCommitteeCommand(
                    name = "Committee",
                    description = "Description",
                    members = members
                )
            )

            assertThat(result).isSameAs(expected)
            verify(committeeService).createWithMembers(
                name = "Committee",
                description = "Description",
                members = members
            )
        }
    }

    @Nested
    inner class UpdateCommittee {

        private val handler = UpdateCommitteeHandler(committeeService)

        @Test
        fun `updates committee with members and version`() {
            val members = mutableListOf(CommitteeMemberData(1L, "Chair"))
            val expected = committee("Updated", "Updated description")
            whenever(
                committeeService.updateWithMembers(
                    id = 3L,
                    name = "Updated",
                    description = "Updated description",
                    members = members,
                    version = 5L
                )
            ).thenReturn(expected)

            val result = handler.handle(
                UpdateCommitteeCommand(
                    id = 3L,
                    name = "Updated",
                    description = "Updated description",
                    members = members,
                    version = 5L
                )
            )

            assertThat(result).isSameAs(expected)
            verify(committeeService).updateWithMembers(
                id = 3L,
                name = "Updated",
                description = "Updated description",
                members = members,
                version = 5L
            )
        }
    }

    @Nested
    inner class DeleteCommitteeById {

        private val handler = DeleteCommitteeByIdHandler(committeeService)

        @Test
        fun `deletes committee by id`() {
            handler.handle(DeleteCommitteeByIdCommand(id = 4L))

            verify(committeeService).deleteById(eq(4L))
        }
    }

    private fun committee(name: String, description: String): Committee = Committee().apply {
        this.name = name
        this.description = description
    }
}
