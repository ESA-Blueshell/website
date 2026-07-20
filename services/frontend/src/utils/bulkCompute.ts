import {type ContributionPeriodResponse} from "@/services/api"
import type {BulkRow} from "@/utils/bulkRow"
import {autoFeeType, effectiveAmount} from "@/utils/feePreview"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Pure FE preview computations for bulk actions. Each function takes BulkTarget[]
 * (derived in UserManager from selectedIds, membershipsByUserId, paidUserIds, and
 * usersById) and returns BulkRow[] with disposition, reason, and type-specific
 * fields (memberType, memberSince, amount, recommendedFeeType).
 * See docs/proposals/bulk-actions/REDESIGN.md §4 & §5.2.
 */

/**
 * Mark as paid: honorary → SKIPPED(HONORARY); already paid → SKIPPED(ALREADY_PAID); else INCLUDED.
 */
export function computeMarkPaidRows(targets: BulkTarget[]): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: "INCLUDED",
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // skipped rows show Type + Member-since (only "—" when there is no membership).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    if (target.isHonorary) {
      row.disposition = "SKIPPED"
      row.reason = "HONORARY"
    } else if (target.mostRecentContribution.paid) {
      row.disposition = "SKIPPED"
      row.reason = "ALREADY_PAID"
    } else {
      row.disposition = "INCLUDED"
    }

    return row
  })
}

/**
 * Mark as unpaid: honorary → SKIPPED(HONORARY); not paid → SKIPPED(NOT_PAID); else INCLUDED.
 */
export function computeMarkUnpaidRows(targets: BulkTarget[]): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: "INCLUDED",
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // skipped rows show Type + Member-since (only "—" when there is no membership).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    if (target.isHonorary) {
      row.disposition = "SKIPPED"
      row.reason = "HONORARY"
    } else if (!target.mostRecentContribution.paid) {
      row.disposition = "SKIPPED"
      row.reason = "NOT_PAID"
    } else {
      row.disposition = "INCLUDED"
    }

    return row
  })
}

/**
 * Send reminder: no membership or honorary → EXCLUDED(HONORARY); no email → SKIPPED(NO_EMAIL);
 * already paid → WARNING(ALREADY_PAID); pays via incasso → WARNING(PAYS_VIA_INCASSO); else INCLUDED.
 *
 * Fee type (auto-selected via autoFeeType): ALUMNI → ALUMNI_FEE;
 * startDate <= cutoffDate → FULL_YEAR_FEE (boundary start == cutoff → FULL);
 * startDate  > cutoffDate → HALF_YEAR_FEE.
 *
 * `flagIncassoPayers` (default true) marks members whose most-recent membership is on
 * incasso as WARNING(PAYS_VIA_INCASSO), off by default. The incasso-notification action
 * passes false so it can apply its own INCASSO_MISMATCH logic instead.
 */
export function computeReminderRows(
  targets: BulkTarget[],
  period: ContributionPeriodResponse | null,
  cutoffDate: string,
  flagIncassoPayers = true,
): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: "INCLUDED",
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // excluded/skipped rows show Type + Member-since (only "—" when there is no membership).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    // Check preconditions
    if (!target.mostRecentMembership || target.isHonorary) {
      row.disposition = "EXCLUDED"
      row.reason = "HONORARY"
      return row
    }

    if (!target.email) {
      row.disposition = "SKIPPED"
      row.reason = "NO_EMAIL"
      return row
    }

    // Auto-select the fee type using the locked rule.
    row.recommendedFeeType = autoFeeType(target.mostRecentMembership, target.isHonorary, cutoffDate) ?? undefined

    // Set amount based on recommended fee type
    row.amount = effectiveAmount(row.recommendedFeeType, period)

    // Disposition: already-paid and incasso-payer are both WARNINGs (off by default,
    // operator can forcibly include). ALREADY_PAID is evaluated first.
    if (target.mostRecentContribution.paid) {
      row.disposition = "WARNING"
      row.reason = "ALREADY_PAID"
    } else if (flagIncassoPayers && target.mostRecentMembership.incasso) {
      row.disposition = "WARNING"
      row.reason = "PAYS_VIA_INCASSO"
    } else {
      row.disposition = "INCLUDED"
    }

    return row
  })
}

/**
 * Send incasso: as reminder plus !incasso → WARNING(INCASSO_MISMATCH).
 */
export function computeIncassoRows(
  targets: BulkTarget[],
  period: ContributionPeriodResponse | null,
  cutoffDate: string,
): BulkRow[] {
  // The incasso action has its own incasso semantics (INCASSO_MISMATCH), so it does not
  // want the reminder's PAYS_VIA_INCASSO flag: pass flagIncassoPayers = false.
  const rows = computeReminderRows(targets, period, cutoffDate, false)

  // Apply incasso-specific logic
  for (let i = 0; i < rows.length; i++) {
    const target = targets[i]!
    const row = rows[i]!

    // If already excluded or skipped, leave it
    if (row.disposition !== "INCLUDED" && row.disposition !== "WARNING") {
      continue
    }

    // Check if most-recent membership is not marked for incasso
    if (target.mostRecentMembership && !target.mostRecentMembership.incasso) {
      row.disposition = "WARNING"
      row.reason = "INCASSO_MISMATCH"
    }
  }

  return rows
}

/**
 * End membership: no active membership or endDate != null → SKIPPED(NO_ACTIVE_MEMBERSHIP); else INCLUDED.
 */
export function computeEndMembershipRows(targets: BulkTarget[], _today: string): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: "INCLUDED",
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // skipped rows show Type + Member-since (only "—" when there is no membership).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    if (!target.mostRecentMembership || target.mostRecentMembership.endDate !== null) {
      row.disposition = "SKIPPED"
      row.reason = "NO_ACTIVE_MEMBERSHIP"
    } else {
      row.disposition = "INCLUDED"
    }

    return row
  })
}

/**
 * Resume membership: mostRecentMembership.endDate == null → SKIPPED(ALREADY_ACTIVE);
 * !latestPeriod → SKIPPED(NO_CONTRIBUTION_PERIOD); endDate within [latestPeriod.startDate, endDate] → INCLUDED(WILL_RESUME);
 * no membership → INCLUDED(WILL_START_NEW); else INCLUDED(WILL_START_NEW).
 */
export function computeResumeMembershipRows(
  targets: BulkTarget[],
  latestPeriod: ContributionPeriodResponse | null,
): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: "INCLUDED",
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // skipped (already-active) rows show Type + Member-since (only "—" when there
    // is no membership at all).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    // If already active (no endDate), skip
    if (target.mostRecentMembership && !target.mostRecentMembership.endDate) {
      row.disposition = "SKIPPED"
      row.reason = "ALREADY_ACTIVE"
      return row
    }

    // If no contribution period, can't resume
    if (!latestPeriod) {
      row.disposition = "SKIPPED"
      row.reason = "NO_CONTRIBUTION_PERIOD"
      return row
    }

    row.disposition = "INCLUDED"

    // Determine the specific reason: WILL_RESUME or WILL_START_NEW
    if (
      target.mostRecentMembership
      && target.mostRecentMembership.endDate
      && target.mostRecentMembership.endDate >= latestPeriod.startDate
      && target.mostRecentMembership.endDate <= latestPeriod.endDate
    ) {
      row.reason = "WILL_RESUME"
    } else {
      row.reason = "WILL_START_NEW"
    }

    return row
  })
}
