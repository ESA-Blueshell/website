package net.blueshell.api.domain.board.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.board.command.*
import net.blueshell.api.domain.board.web.dto.request.AddBoardMemberRequest
import net.blueshell.api.domain.board.web.dto.request.CreateBoardRequest
import net.blueshell.api.domain.board.web.dto.request.UpdateBoardRequest
import net.blueshell.api.domain.board.web.dto.response.BoardMemberResponse
import net.blueshell.api.domain.board.web.dto.response.BoardResponse
import net.blueshell.api.domain.board.web.mapping.asCommand
import net.blueshell.api.domain.board.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/boards")
@Tag(name = "Boards")
class BoardController(
    private val commandBus: CommandBus
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'Board', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBoard(@Valid @RequestBody request: CreateBoardRequest): BoardResponse {
        val board = commandBus.dispatch(request.asCommand())
        return board.asResponse()
    }

    @GetMapping
    @PermitAll
    fun findAllBoards(): List<BoardResponse> {
        return commandBus.dispatch(FindBoardsCommand())
            .map { it.asResponse() }
    }

    @GetMapping("/{id}")
    @PermitAll
    fun findBoardById(@PathVariable id: Long): BoardResponse {
        val board = commandBus.dispatch(FindBoardByIdCommand(id))
        return board.asResponse()
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Board', 'write')")
    fun updateBoard(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBoardRequest
    ): BoardResponse {
        val board = commandBus.dispatch(request.asCommand(id))
        return board.asResponse()
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Board', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBoard(@PathVariable id: Long) {
        commandBus.dispatch(DeleteBoardByIdCommand(id))
    }

    // Board Member endpoints
    @PostMapping("/{boardId}/members")
    @PreAuthorize("hasPermission(#boardId, 'Board', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable boardId: Long,
        @Valid @RequestBody request: AddBoardMemberRequest
    ): BoardMemberResponse {
        val member = commandBus.dispatch(request.asCommand(boardId))
        return member.asResponse()
    }

    @DeleteMapping("/{boardId}/members/{userId}")
    @PreAuthorize("hasPermission(#boardId, 'Board', 'write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @PathVariable boardId: Long,
        @PathVariable userId: Long
    ) {
        commandBus.dispatch(RemoveBoardMemberCommand(boardId, userId))
    }
}
