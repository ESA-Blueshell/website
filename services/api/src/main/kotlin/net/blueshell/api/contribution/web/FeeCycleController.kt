package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.contribution.domain.FeeCycleEmailPreview
import net.blueshell.api.contribution.domain.FeeCycleEmailPreviewService
import net.blueshell.api.contribution.domain.FeeCyclePlan
import net.blueshell.api.contribution.domain.FeeCycleResult
import net.blueshell.api.contribution.domain.FeeCycleUseCases
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The fee cycle for one contribution period: read it, then send it.
 *
 * Board-only, matching the two records it writes. Both endpoints are anchored to a period —
 * there is no cycle without one, which is why neither takes a list of members.
 */
@RestController
@Tag(name = "Contributions")
class FeeCycleController(
    private val useCases: FeeCycleUseCases,
    private val emailPreviews: FeeCycleEmailPreviewService,
) {
    @PreAuthorize(
        "hasPermission('__NO_TARGET__', 'ContributionReminder', 'write') " +
            "and hasPermission('__NO_TARGET__', 'IncassoNotification', 'write')",
    )
    @GetMapping("/contributions/fee-cycle")
    fun previewFeeCycle(@RequestParam contributionPeriodId: Long): FeeCyclePreviewResponse =
        useCases.preview(contributionPeriodId).asResponse()

    @PreAuthorize(
        "hasPermission('__NO_TARGET__', 'ContributionReminder', 'write') " +
            "and hasPermission('__NO_TARGET__', 'IncassoNotification', 'write')",
    )
    @PostMapping("/contributions/fee-cycle/send")
    fun sendFeeCycle(@Valid @RequestBody request: SendFeeCycleRequest): FeeCycleResultResponse =
        useCases.send(
            contributionPeriodId = requireNotNull(request.contributionPeriodId),
            paymentDueDate = requireNotNull(request.paymentDueDate),
            debitDate = requireNotNull(request.debitDate),
            feeTypeOverrides = request.feeTypeOverrides,
        ).asResponse()

    /**
     * One member's email, as they would receive it. Reading it sends nothing and records
     * nothing, and which of the two statements comes back is the member's own side of the
     * partition rather than a parameter.
     */
    @PreAuthorize(
        "hasPermission('__NO_TARGET__', 'ContributionReminder', 'write') " +
            "and hasPermission('__NO_TARGET__', 'IncassoNotification', 'write')",
    )
    @GetMapping("/contributions/fee-cycle/email-preview")
    fun previewFeeCycleEmail(
        @RequestParam contributionPeriodId: Long,
        @RequestParam userId: Long,
        @RequestParam paymentDueDate: LocalDate,
        @RequestParam debitDate: LocalDate,
        @RequestParam(required = false) feeType: BulkFeeType?,
    ): FeeCycleEmailPreviewResponse =
        emailPreviews.preview(contributionPeriodId, userId, paymentDueDate, debitDate, feeType).asResponse()
}

private fun FeeCycleEmailPreview.asResponse(): FeeCycleEmailPreviewResponse = FeeCycleEmailPreviewResponse(
    group = group,
    feeType = feeType,
    subject = subject,
    html = html,
    recipientEmail = recipientEmail,
    recipientName = recipientName,
)

private fun FeeCyclePlan.asResponse(): FeeCyclePreviewResponse = FeeCyclePreviewResponse(
    contributionPeriodId = contributionPeriodId,
    rows = participants.map { participant ->
        FeeCycleRowResponse(
            userId = participant.userId,
            name = participant.name,
            memberType = participant.memberType,
            memberSince = participant.memberSince,
            group = participant.group,
            disposition = participant.disposition,
            reason = participant.reason,
            feeType = participant.feeType,
            amount = participant.amount,
            lastAskedOn = participant.lastAskedOn,
        )
    },
)

private fun FeeCycleResult.asResponse(): FeeCycleResultResponse = FeeCycleResultResponse(
    paymentRequestsQueued = paymentRequestsQueued,
    preNotificationsQueued = preNotificationsQueued,
    excluded = excluded,
)
