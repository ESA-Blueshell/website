package net.blueshell.api.domain.board.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.shared.command.Command
import java.time.LocalDate

data class CreateBoardCommand(
    @field:NotBlank(message = "Board name is required")
    @field:Size(min = 1, max = 100, message = "Board name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Candidate name is required")
    val candidate: String,

    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate,

    val endDate: LocalDate? = null,

    val pictureId: Long? = null
) : Command<Board>

data class UpdateBoardCommand(
    @field:NotNull(message = "Board ID is required")
    val id: Long,

    @field:NotBlank(message = "Board name is required")
    @field:Size(min = 1, max = 100, message = "Board name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Candidate name is required")
    val candidate: String,

    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate,

    val endDate: LocalDate? = null,

    val pictureId: Long? = null,

    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<Board>

data class FindBoardByIdCommand(
    @field:NotNull(message = "Board ID is required")
    val id: Long
) : Command<Board>

class FindBoardsCommand : Command<MutableList<Board>>

data class DeleteBoardByIdCommand(
    @field:NotNull(message = "Board ID is required")
    val id: Long
) : Command<Unit>

// Board Member Commands
data class AddBoardMemberCommand(
    @field:NotNull(message = "Board ID is required")
    val boardId: Long,

    @field:NotNull(message = "User ID is required")
    val userId: Long,

    @field:NotBlank(message = "Role is required")
    val role: String,

    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate,

    val endDate: LocalDate? = null
) : Command<BoardMember>

data class RemoveBoardMemberCommand(
    @field:NotNull(message = "Board ID is required")
    val boardId: Long,

    @field:NotNull(message = "User ID is required")
    val userId: Long
) : Command<Unit>
