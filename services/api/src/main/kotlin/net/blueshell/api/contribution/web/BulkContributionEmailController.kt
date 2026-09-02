package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.contribution.domain.BulkContributionEmailUseCases
import net.blueshell.api.contribution.domain.ContributionEmailKind
import net.blueshell.api.contribution.domain.ContributionEmailMessage
import net.blueshell.api.contribution.domain.ContributionEmailMessageService
import net.blueshell.api.contribution.domain.ContributionEmailPlan
import net.blueshell.api.contribution.domain.ContributionEmailResult
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * A period's payment emails: read what a send would do, read one member's email, then send.
 * Every endpoint needs write on both records, because one send writes both.
 */
@RestController
@Tag(name = "Contributions")
class BulkContributionEmailController(
    private val useCases: BulkContributionEmailUseCases,
    private val messages: ContributionEmailMessageService,
) {
    @PreAuthorize(BOTH_STATEMENTS)
    @PostMapping("/contributions/bulk/email/preview")
    fun previewBulkContributionEmail(
        @Valid @RequestBody request: BulkContributionEmailPreviewRequest,
    ): BulkContributionEmailPreviewResponse =
        useCases.preview(
            contributionPeriodId = requireNotNull(request.contributionPeriodId),
            userIds = request.userIds,
        ).asResponse()

    /** Reading sends nothing and records nothing. */
    @PreAuthorize(BOTH_STATEMENTS)
    @GetMapping("/contributions/bulk/email/message")
    fun readContributionEmail(
        @RequestParam kind: ContributionEmailKind,
        @RequestParam contributionPeriodId: Long,
        @RequestParam userId: Long,
        @RequestParam date: LocalDate,
        @RequestParam(required = false) feeType: BulkFeeType?,
    ): ContributionEmailMessageResponse =
        messages.render(kind, contributionPeriodId, userId, date, feeType).asResponse()

    @PreAuthorize(BOTH_STATEMENTS)
    @PostMapping("/contributions/bulk/email/send")
    fun sendPaymentEmails(@Valid @RequestBody request: SendPaymentEmailsRequest): PaymentEmailsResultResponse =
        useCases.send(
            contributionPeriodId = requireNotNull(request.contributionPeriodId),
            userIds = request.userIds,
            forciblyIncluded = request.forciblyIncludedUserIds.toSet(),
            kindOverrides = request.kindOverrides,
            paymentDueDate = request.paymentDueDate,
            debitDate = request.debitDate,
            feeTypeOverrides = request.feeTypeOverrides,
        ).asResponse()

    private companion object {
        const val BOTH_STATEMENTS =
            "hasPermission('__NO_TARGET__', 'ContributionReminder', 'write') " +
                "and hasPermission('__NO_TARGET__', 'IncassoNotification', 'write')"
    }
}

private fun ContributionEmailPlan.asResponse() = BulkContributionEmailPreviewResponse(
    contributionPeriodId = contributionPeriodId,
    rows = rows.map { row ->
        BulkContributionEmailRowResponse(
            userId = row.userId,
            name = row.name,
            memberType = row.memberType,
            memberSince = row.memberSince,
            disposition = row.disposition,
            reason = row.reason,
            defaultKind = row.defaultKind,
            feeType = row.feeType,
            amount = row.amount,
            lastRemindedOn = row.lastRemindedOn,
            lastNotifiedOn = row.lastNotifiedOn,
        )
    },
    unknownUserIds = unknownUserIds,
)

private fun ContributionEmailMessage.asResponse() = ContributionEmailMessageResponse(
    kind = kind,
    feeType = feeType,
    subject = subject,
    html = html,
    recipientEmail = recipientEmail,
    recipientName = recipientName,
)

private fun ContributionEmailResult.asResponse() = PaymentEmailsResultResponse(
    remindersSent = remindersSent,
    incassoNotificationsSent = incassoNotificationsSent,
    notWrittenTo = notWrittenTo,
)
