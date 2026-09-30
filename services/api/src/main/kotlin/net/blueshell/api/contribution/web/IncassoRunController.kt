package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import net.blueshell.api.contribution.domain.IncassoCandidate
import net.blueshell.api.contribution.domain.IncassoRunView
import net.blueshell.api.contribution.domain.IncassoRuns
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Schema(name = "StartIncassoRunRequest")
data class StartIncassoRunRequest(
    val userIds: List<Long> = emptyList(),
    /** Only for a member whose fee type differs from the one the period gives them. */
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
    @field:NotNull
    val collectionDate: LocalDate?,
    val statementText: String = "",
)

/** Collecting a period's contributions by incasso, for the treasurer and the board. */
@RestController
@Tag(name = "Contributions")
class IncassoRunController(
    private val runs: IncassoRuns,
    private val currentUser: CurrentUserProvider,
) {
    /** Everybody on incasso in the period, and why anybody among them is left out. */
    @BoardOnly
    @GetMapping("/contributionPeriods/{periodId}/incasso")
    fun planIncasso(
        @PathVariable periodId: Long,
    ): List<IncassoCandidate> = runs.plan(periodId)

    /** Records the run and emails each member the incasso notification. */
    @BoardOnly
    @PostMapping("/contributionPeriods/{periodId}/incassoRuns")
    @ResponseStatus(HttpStatus.CREATED)
    fun startIncassoRun(
        @PathVariable periodId: Long,
        @Valid @RequestBody request: StartIncassoRunRequest,
    ): IncassoRunView =
        runs.start(
            periodId,
            request.userIds,
            request.feeTypeOverrides,
            requireNotNull(request.collectionDate),
            request.statementText,
            currentUser.currentUser()?.id,
        )

    @BoardOnly
    @GetMapping("/incassoRuns/{runId}")
    fun findIncassoRun(
        @PathVariable runId: Long,
    ): IncassoRunView = runs.find(runId)
}
