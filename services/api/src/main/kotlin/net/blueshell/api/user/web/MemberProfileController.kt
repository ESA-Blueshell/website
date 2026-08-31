package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.user.domain.MemberProfileUseCases
import net.blueshell.api.user.api.UserService
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.sql.Date

@RestController
@RequestMapping
@Tag(name = "Member Profiles")
class MemberProfileController(
    service: UserService,
    private val useCases: MemberProfileUseCases,
) : BaseController<UserService>(service) {
    @PostMapping("/memberProfiles")
    @PreAuthorize("hasPermission(#request.userId, 'User', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createMemberProfile(@Valid @RequestBody request: CreateMemberProfileRequest): MemberProfileResponse {
        val memberProfile = useCases.create(
            userId = request.userId,
            dateOfBirth = Date.valueOf(request.dateOfBirth),
            studentNumber = request.studentNumber,
            gender = request.gender,
            nationality = request.nationality,
            bhv = request.bhv,
            ehbo = request.ehbo,
            nameOnRosters = request.nameOnRosters,
        )
        return memberProfile.asResponse()
    }

    @PutMapping("/users/{userId}/memberProfiles")
    @PreAuthorize("hasPermission(#userId, 'User', 'write')")
    fun updateMemberProfile(
        @PathVariable userId: Long,
        @Valid @RequestBody request: UpdateMemberProfileRequest
    ): MemberProfileResponse {
        val memberProfile = useCases.update(
            userId = userId,
            dateOfBirth = Date.valueOf(request.dateOfBirth),
            studentNumber = request.studentNumber,
            gender = request.gender,
            nationality = request.nationality,
            bhv = request.bhv,
            ehbo = request.ehbo,
            nameOnRosters = request.nameOnRosters,
            version = request.version,
        )
        return memberProfile.asResponse()
    }

    @GetMapping("/users/{userId}/memberProfiles")
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    fun findMemberProfileByUserId(@PathVariable userId: Long): MemberProfileResponse {
        val memberProfile = useCases.findByUserId(userId)
        return memberProfile.asResponse()
    }
}
