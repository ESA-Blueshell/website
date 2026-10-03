package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.FirstContribution
import net.blueshell.api.contribution.domain.FirstContributions
import net.blueshell.api.contribution.domain.MemberContributions
import net.blueshell.api.contribution.domain.MemberPeriodContribution
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Tag(name = "Contributions")
class MemberContributionController(
    private val contributions: MemberContributions,
    private val firsts: FirstContributions,
    private val currentUser: CurrentUserProvider,
) {
    /** What the reader pays to make their pending membership active; nothing where none is pending. */
    @GetMapping("/users/me/first-contribution")
    @PreAuthorize("isAuthenticated()")
    fun findOwnFirstContribution(): ResponseEntity<FirstContribution> {
        val reader = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return firsts.owedBy(reader)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()
    }

    @GetMapping("/users/{userId}/contributions")
    @BoardOnly
    fun findMemberContributions(
        @PathVariable userId: Long,
    ): List<MemberPeriodContribution> = contributions.of(userId)
}
