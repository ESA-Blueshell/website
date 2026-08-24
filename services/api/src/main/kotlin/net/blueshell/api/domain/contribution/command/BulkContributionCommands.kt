package net.blueshell.api.domain.contribution.command

import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.dto.bulk.BulkActionResult

/** Which way a bulk contribution request moves the selected users. */
enum class BulkContributionOperation { PAID, UNPAID }

/**
 * Records, or un-records, a contribution for every selected user in one period.
 * Applied whole: a selection naming a user the action cannot touch is refused.
 */
data class ExecuteBulkContributionCommand(
    val userIds: List<Long>,
    val contributionPeriodId: Long,
    val operation: BulkContributionOperation,
) : Command<BulkActionResult>
