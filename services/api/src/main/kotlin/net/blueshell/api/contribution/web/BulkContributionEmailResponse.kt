package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.contribution.domain.ContributionEmailKind
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

@Schema(name = "BulkContributionEmailRowResponse")
data class BulkContributionEmailRowResponse(
    val userId: Long,
    val name: String,
    val memberType: MemberType,

    @field:Schema(description = "Start of the membership every decision on this row was judged against.")
    val memberSince: LocalDate?,

    val disposition: BulkRowDisposition,

    @field:Schema(description = "Why this member is warned about or not written to.")
    val reason: BulkRowReason?,

    @field:Schema(description = "The email this member gets unless the treasurer switches it.")
    val defaultKind: ContributionEmailKind,

    @field:Schema(description = "Absent only for honorary members, who owe nothing.")
    val feeType: BulkFeeType?,

    @field:Schema(description = "Follows from the fee type and the period. Never typed.")
    val amount: Double?,

    val lastRemindedOn: LocalDate?,
    val lastNotifiedOn: LocalDate?,
)

/** A member who will not be written to is present with their reason rather than left out. */
@Schema(name = "BulkContributionEmailPreviewResponse")
data class BulkContributionEmailPreviewResponse(
    val contributionPeriodId: Long,
    val rows: List<BulkContributionEmailRowResponse>,
)

@Schema(name = "ContributionEmailMessageResponse")
data class ContributionEmailMessageResponse(
    val kind: ContributionEmailKind,
    val feeType: BulkFeeType,
    val subject: String,
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
)

@Schema(name = "PaymentEmailsResultResponse")
data class PaymentEmailsResultResponse(
    val remindersSent: Int,
    val incassoNotificationsSent: Int,

    @field:Schema(description = "Members in the selection who were not written to.")
    val notWrittenTo: Int,
)
