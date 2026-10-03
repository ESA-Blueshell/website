package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.UnlinkedTargets
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Schema(name = "UnlinkedTarget", description = "A role or list somebody belongs on, which they reach once they link their account there")
data class UnlinkedTargetResponse(
    val system: TargetSystem,
    val label: String,
)

@RestController
@Tag(name = "Cohorts")
class UnlinkedTargetController(
    private val unlinked: UnlinkedTargets,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping("/users/me/unlinked-targets")
    @PreAuthorize("isAuthenticated()")
    fun listMyUnlinkedTargets(): List<UnlinkedTargetResponse> {
        val me = currentUser.currentUser() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return unlinked.of(me.id).map { UnlinkedTargetResponse(it.system, it.label) }
    }
}
