package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.domain.CommitteeSeats
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.api.Image
import net.blueshell.api.file.api.asImage
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@Tag(name = "Committees")
class CommitteeController(
    service: CommitteeService,
    private val seats: CommitteeSeats,
    private val files: FileService,
) : AdvancedController<CommitteeService>(
        service,
    ) {
    @GetMapping("/committeeMembers/committees")
    @PermitAll
    fun findCommitteesByUserId(): MutableList<CommitteeResponse> {
        val principal = SecurityUtils.currentPrincipal()
        val principalId = principal?.id ?: return mutableListOf()
        val includeAll = principal.hasAuthority(Role.BOARD)
        val committees = if (includeAll) service.findAll() else service.findAllByUserId(principalId)
        return committees.map { it.asDetailResponse() }.toMutableList()
    }

    @GetMapping("/committees")
    @PermitAll
    fun findCommittees(): MutableList<CommitteeResponse> {
        val committees = service.findAll()
        // Taken from the security context rather than bound as a request parameter, so the
        // detail level is picked from a server-held value only.
        return if (SecurityUtils.hasAuthority(Role.BOARD)) {
            committees.map { it.asDetailResponse() }.toMutableList()
        } else {
            committees.map { it.asSummaryResponse() }.toMutableList()
        }
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    fun findCommitteeById(
        @PathVariable committeeId: Long,
    ): CommitteeResponse {
        val committee = service.findById(committeeId)
        // Taken from the security context rather than bound as a request parameter, so the
        // detail level is picked from a server-held value only.
        val principal = SecurityUtils.currentPrincipal()
        if (principal?.hasAuthority(Role.BOARD) == true || committee.hasMember(principal?.id)) {
            return committee.asDetailResponse()
        }

        return committee.asSummaryResponse()
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Committee', 'write')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(
        @Valid @RequestBody request: @Valid CreateCommitteeRequest,
    ): CommitteeDetailResponse {
        val committee =
            service.createWithMembers(
                name = request.name,
                description = request.description,
                members = request.members.map { it.asData() }.toMutableList(),
                page = request.page(),
            )
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Committee', 'write')")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody request: @Valid UpdateCommitteeRequest,
    ): CommitteeDetailResponse {
        val committee =
            service.updateWithMembers(
                id = id,
                name = request.name,
                description = request.description,
                members = request.members.map { it.asData() }.toMutableList(),
                version = request.version,
                page = request.page(),
            )
        return committee.asDetailResponse()
    }

    /** A committee's public page, by the address it answers to. Its members by Discord only. */
    @GetMapping("/committees/address/{address}")
    @PermitAll
    fun findCommitteePage(
        @PathVariable address: String,
    ): CommitteePageResponse {
        val committee = service.findByAddress(address)
        return committee.asPageResponse(seats.of(committee))
    }

    /** What the committee's own members change about it: its description, banner and games. */
    @PreAuthorize("hasPermission(#id, 'Committee', 'page')")
    @PutMapping("/committees/{id}/page")
    fun updateCommitteePage(
        @PathVariable id: Long,
        @Valid @RequestBody request: CommitteeOwnPageRequest,
    ): CommitteeDetailResponse =
        service
            .updateOwnPage(
                id = id,
                description = request.description,
                banner = request.banner,
                gameCodes = request.gameCodes,
                version = request.version,
            ).asDetailResponse()

    /** A banner the committee's own members chose, stored so a save can point at it. The id is read by the permission. */
    @Suppress("UnusedParameter")
    @PreAuthorize("hasPermission(#id, 'Committee', 'page')")
    @PostMapping("/committees/{id}/banners", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadCommitteeBanner(
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile,
    ): Image = files.storeMultipart(file, FileType.COMMITTEE_BANNER).asImage()

    @PreAuthorize("hasPermission(#id, 'Committee', 'write')")
    @PutMapping("/committees/{id}/archived")
    fun archiveCommittee(
        @PathVariable id: Long,
        @RequestBody request: ArchiveCommitteeRequest,
    ): CommitteeDetailResponse = service.archive(id, request.archived).asDetailResponse()

    /** Sets which committees organise events for a game, from the game's own form. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Committee', 'write')")
    @PutMapping("/committees/games/{game}")
    fun setGameOrganisers(
        @PathVariable game: String,
        @RequestBody request: GameOrganisersRequest,
    ): List<CommitteeResponse> = service.organisersOf(game, request.committeeIds.toSet()).map { it.asSummaryResponse() }

    @PreAuthorize("hasPermission(#id, 'Committee', 'delete')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(
        @PathVariable id: Long,
    ) {
        service.deleteById(id)
    }
}
