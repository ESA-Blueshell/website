import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import type {BulkPreviewRow} from "@/services/api/blueshell/types.gen"
import {effectiveAmount, type FeeType} from "@/utils/feePreview"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Pure FE preview computations for bulk actions. Each function takes BulkTarget[]
 * (derived in MemberManager from selectedIds, membershipsByUserId, paidUserIds, and
 * usersById) and returns BulkPreviewRow[] with disposition, reason, and type-specific
 * fields (memberType, memberSince, amount, recommendedFeeType).
 * See docs/proposals/bulk-actions/REDESIGN.md §4 & §5.2.
 */

/**
 * Mark as paid: honorary → SKIPPED(HONORARY); already paid → SKIPPED(ALREADY_PAID); else INCLUDED.
 */
export function computeMarkPaidRows(targets: BulkTarget[]): BulkPreviewRow[] {
  return targets.map((target) => {
    const row: BulkPreviewRow = {
      userId: target.userId,
      name: target.name,
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

    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    return row
  })
}

/**
 * Mark as unpaid: honorary → SKIPPED(HONORARY); not paid → SKIPPED(NOT_PAID); else INCLUDED.
 */
export function computeMarkUnpaidRows(targets: BulkTarget[]): BulkPreviewRow[] {
  return targets.map((target) => {
    const row: BulkPreviewRow = {
      userId: target.userId,
      name: target.name,
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

    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    return row
  })
}

/**
 * Send reminder: no membership or honorary → EXCLUDED(HONORARY); no email → SKIPPED(NO_EMAIL);
 * already paid → WARNING(ALREADY_PAID); else INCLUDED. Fee type: ALUMNI → ALUMNI_FEE;
 * startDate >= cutoffDate → HALF_YEAR_FEE; else FULL_YEAR_FEE.
 */
export function computeReminderRows(
  targets: BulkTarget[],
  period: ContributionPeriodResponse | null,
  cutoffDate: string,
): BulkPreviewRow[] {
  return targets.map((target) => {
    const row: BulkPreviewRow = {
      userId: target.userId,
      name: target.name,
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

    // Populate membership info
    row.memberType = target.mostRecentMembership.type
    row.memberSince = target.mostRecentMembership.startDate

    // Determine fee type recommendation
    if (target.mostRecentMembership.type === MemberType.ALUMNI) {
      row.recommendedFeeType = "ALUMNI_FEE"
    } else if (target.mostRecentMembership.startDate >= cutoffDate) {
      row.recommendedFeeType = "HALF_YEAR_FEE"
    } else {
      row.recommendedFeeType = "FULL_YEAR_FEE"
    }

    // Set amount based on recommended fee type
    row.amount = effectiveAmount(row.recommendedFeeType, period)

    // Check if already paid
    if (target.mostRecentContribution.paid) {
      row.disposition = "WARNING"
      row.reason = "ALREADY_PAID"
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
): BulkPreviewRow[] {
  const rows = computeReminderRows(targets, period, cutoffDate)

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
export function computeEndMembershipRows(targets: BulkTarget[], today: string): BulkPreviewRow[] {
  return targets.map((target) => {
    const row: BulkPreviewRow = {
      userId: target.userId,
      name: target.name,
    }

    if (!target.mostRecentMembership || target.mostRecentMembership.endDate !== null) {
      row.disposition = "SKIPPED"
      row.reason = "NO_ACTIVE_MEMBERSHIP"
    } else {
      row.disposition = "INCLUDED"
    }

    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
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
): BulkPreviewRow[] {
  return targets.map((target) => {
    const row: BulkPreviewRow = {
      userId: target.userId,
      name: target.name,
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

    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    return row
  })
}
