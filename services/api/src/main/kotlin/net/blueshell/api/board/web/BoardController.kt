package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.board.domain.BoardUseCases
import net.blueshell.api.security.BoardOnly
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/boards")
@Tag(name = "Boards")
class BoardController(
    private val useCases: BoardUseCases,
) {
    @PostMapping
    @BoardOnly
    @ResponseStatus(HttpStatus.CREATED)
    fun createBoard(
        @Valid @RequestBody request: BoardRequest,
    ): BoardResponse = useCases.create(request.asInput()).asResponse()

    @GetMapping
    @PermitAll
    fun findAllBoards(): List<BoardResponse> = useCases.all().map { it.asResponse() }

    @GetMapping("/{id}")
    @PermitAll
    fun findBoardById(
        @PathVariable id: Long,
    ): BoardResponse = useCases.byId(id).asResponse()

    @PutMapping("/{id}")
    @BoardOnly
    fun updateBoard(
        @PathVariable id: Long,
        @Valid @RequestBody request: BoardRequest,
    ): BoardResponse = useCases.update(id, request.asInput(), request.version).asResponse()

    @DeleteMapping("/{id}")
    @BoardOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBoard(
        @PathVariable id: Long,
    ) {
        useCases.remove(id)
    }

    // Board Member endpoints
    @PostMapping("/{boardId}/members")
    @BoardOnly
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable boardId: Long,
        @Valid @RequestBody request: AddBoardMemberRequest,
    ): BoardMemberResponse = useCases.addMember(boardId, request.userId, request.asInput()).asResponse()

    @PutMapping("/{boardId}/members/{id}")
    @BoardOnly
    // The member id names the row; boardId only keeps the path readable.
    @Suppress("UnusedParameter")
    fun updateMember(
        @PathVariable boardId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBoardMemberRequest,
    ): BoardMemberResponse = useCases.updateMember(id, request.asInput()).asResponse()

    /** A null account detaches the membership, leaving the history standing under its own name. */
    @PutMapping("/{boardId}/members/{id}/member")
    @BoardOnly
    // The member id names the row; boardId only keeps the path readable.
    @Suppress("UnusedParameter")
    fun linkMember(
        @PathVariable boardId: Long,
        @PathVariable id: Long,
        @RequestBody request: LinkBoardMemberRequest,
    ): BoardMemberResponse = useCases.linkMember(id, request.userId).asResponse()

    @DeleteMapping("/{boardId}/members/{id}")
    @BoardOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // The member id names the row; boardId only keeps the path readable.
    @Suppress("UnusedParameter")
    fun removeMember(
        @PathVariable boardId: Long,
        @PathVariable id: Long,
    ) {
        useCases.removeMember(id)
    }
}
