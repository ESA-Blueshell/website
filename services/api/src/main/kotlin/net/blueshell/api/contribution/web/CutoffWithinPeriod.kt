package net.blueshell.api.contribution.web

import java.time.LocalDate

/**
 * Whether a half-year cutoff is a point in the year it is policy for.
 *
 * Inclusive of both ends, and deliberately so: a cutoff on the start date prices every
 * membership that began after the period opened as a half year, and one on the end date
 * prices every membership as a full year. Both are policies a treasurer might mean, and
 * both are what V99 backfills an existing period to when its midpoint clamps to a
 * boundary — a stricter rule here would leave such a period unable to be saved at all.
 *
 * One function rather than the same expression in both requests, so the create and the
 * update endpoints cannot come to disagree about what a valid cutoff is.
 */
internal fun cutoffWithinPeriod(cutoff: LocalDate, startDate: LocalDate, endDate: LocalDate): Boolean =
    !cutoff.isBefore(startDate) && !cutoff.isAfter(endDate)
