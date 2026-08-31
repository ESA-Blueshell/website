package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import java.time.LocalDate

/**
 * What a bulk membership action would do to each selected member, decided by the server.
 *
 * The dialog renders this rather than working the rows out for itself, so the answer the
 * operator confirms is the answer the api will act on. The names and member types beside
 * each row still come from the table the page already loaded; only the disposition and the
 * date are the server's to say.
 */
@Schema(name = "BulkMembershipPreview")
data class BulkMembershipPreviewResponse(
    /**
     * The date the action will be applied on, read from the server's clock. The page shows
     * this and measures its own boundaries against it, so a member in another timezone is
     * told the same thing as everybody else.
     */
    val effectiveDate: LocalDate,
    val rows: List<BulkMembershipPreviewRowResponse>,
)

/** One member's place in a bulk membership action. */
@Schema(name = "BulkMembershipPreviewRow")
data class BulkMembershipPreviewRowResponse(
    val userId: Long,
    val disposition: BulkRowDisposition,
    /** Why the row is not included, or what it will do when it is. Null when there is nothing to add. */
    val reason: BulkRowReason?,
)
