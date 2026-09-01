package net.blueshell.api.board.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.board.domain.BoardService
import net.blueshell.api.board.domain.BoardUseCases
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/boards")
@Tag(name = "Boards")
class BoardController(
    private val service: BoardService,
    private val useCases: BoardUseCases,
) {
    @PostMapping
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Board', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBoard(@Valid @RequestBody request: CreateBoardRequest): BoardResponse {
        val board = useCases.create(
            number = request.number,
            name = request.name,
            candidate = request.candidate,
            startDate = request.startDate,
            endDate = request.endDate,
            pictureId = request.pictureId,
            cheer = request.cheer,
            accent = request.accent,
            description = request.description,
            image = request.image,
        )
        return board.asResponse()
    }

    @GetMapping
    @PermitAll
    fun findAllBoards(): List<BoardResponse> {
        return service.findAll().map { it.asResponse() }
    }

    @GetMapping("/{id}")
    @PermitAll
    fun findBoardById(@PathVariable id: Long): BoardResponse {
        return service.findById(id).asResponse()
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Board', 'write')")
    fun updateBoard(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBoardRequest
    ): BoardResponse {
        val board = useCases.update(
            id = id,
            number = request.number,
            name = request.name,
            candidate = request.candidate,
            startDate = request.startDate,
            endDate = request.endDate,
            pictureId = request.pictureId,
            cheer = request.cheer,
            accent = request.accent,
            description = request.description,
            image = request.image,
        )
        return board.asResponse()
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Board', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBoard(@PathVariable id: Long) {
        service.deleteById(id)
    }

    // Board Member endpoints
    @PostMapping("/{boardId}/members")
    @PreAuthorize("hasPermission(#boardId, 'Board', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable boardId: Long,
        @Valid @RequestBody request: AddBoardMemberRequest
    ): BoardMemberResponse {
        val member = useCases.addMember(
            boardId = boardId,
            userId = request.userId,
            role = request.role,
            startDate = request.startDate,
            endDate = request.endDate,
            displayName = request.displayName,
            nickname = request.nickname,
            description = request.description,
            image = request.image,
        )
        return member.asResponse()
    }

    @PutMapping("/{boardId}/members/{id}")
    @PreAuthorize("hasPermission(#boardId, 'Board', 'write')")
    fun updateMember(
        @PathVariable boardId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBoardMemberRequest
    ): BoardMemberResponse {
        val member = useCases.updateMember(
            id = id,
            role = request.role,
            startDate = request.startDate,
            endDate = request.endDate,
            displayName = request.displayName,
            nickname = request.nickname,
            description = request.description,
            image = request.image,
        )
        return member.asResponse()
    }

    /** A null member detaches the seat, leaving the history standing under its own name. */
    @PutMapping("/{boardId}/members/{id}/member")
    @PreAuthorize("hasPermission(#boardId, 'Board', 'write')")
    fun linkMember(
        @PathVariable boardId: Long,
        @PathVariable id: Long,
        @RequestBody request: LinkBoardMemberRequest
    ): BoardMemberResponse {
        return useCases.linkMember(id, request.userId).asResponse()
    }

    @DeleteMapping("/{boardId}/members/{id}")
    @PreAuthorize("hasPermission(#boardId, 'Board', 'write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @PathVariable boardId: Long,
        @PathVariable id: Long
    ) {
        useCases.removeMember(id)
    }
}
