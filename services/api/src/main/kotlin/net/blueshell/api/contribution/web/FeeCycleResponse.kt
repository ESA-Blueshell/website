package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.shared.enums.MemberType
import java.time.LocalDate

/** One member's row in the fee cycle: which side they are on, what they owe and why. */
@Schema(name = "FeeCycleRowResponse")
data class FeeCycleRowResponse(
    val userId: Long,
    val name: String,
    val memberType: MemberType,

    @field:Schema(description = "Start of the membership every decision on this row was judged against.")
    val memberSince: LocalDate?,

    @field:Schema(description = "Decided by the member's direct-debit flag, not by the operator.")
    val group: FeeCycleGroup,

    val disposition: BulkRowDisposition,

    @field:Schema(description = "Why this member is not written to. Absent on an included row.")
    val reason: BulkRowReason?,

    @field:Schema(description = "Absent only for honorary members, who owe nothing.")
    val feeType: BulkFeeType?,

    @field:Schema(description = "Follows from the fee type and the period. Never typed.")
    val amount: Double?,

    @field:Schema(description = "When this member was last asked for this period, on this side of the partition.")
    val lastAskedOn: LocalDate?,
)

/**
 * The whole cycle for one period: every member of it who has not paid.
 *
 * Rows that will not be written to are present with their reason rather than left out, so a
 * member's absence from the send is visible.
 */
@Schema(name = "FeeCyclePreviewResponse")
data class FeeCyclePreviewResponse(
    val contributionPeriodId: Long,
    val rows: List<FeeCycleRowResponse>,
)

/** What a send did, per side of the partition. */
@Schema(name = "FeeCycleResultResponse")
data class FeeCycleResultResponse(
    val paymentRequestsQueued: Int,
    val preNotificationsQueued: Int,

    @field:Schema(description = "Members in the cycle who were not written to.")
    val excluded: Int,
)

/**
 * A fee-cycle email as the member would receive it, and which of the two statements it is.
 *
 * Rendered through the shared preview renderer from the same `EmailContent` the send path
 * builds, with hosted images inlined so the pictures show in a browser that cannot reach
 * the asset host.
 */
@Schema(name = "FeeCycleEmailPreviewResponse")
data class FeeCycleEmailPreviewResponse(
    @field:Schema(description = "Which statement this member receives, decided by their direct-debit flag.")
    val group: FeeCycleGroup,

    @field:Schema(description = "The fee type the email states, and the reason it gives.")
    val feeType: BulkFeeType,

    val subject: String,
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
)
