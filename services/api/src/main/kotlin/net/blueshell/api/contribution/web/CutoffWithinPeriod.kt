package net.blueshell.api.contribution.web

import java.time.LocalDate

/**
 * Whether a half-year cutoff is a point in the year it is policy for.
 *
 * Inclusive of both ends, deliberately: a cutoff on the start date prices everything as a half
 * year and one on the end date prices everything as a full year, both policies a treasurer
 * might mean and both what a backfilled period clamps to. A stricter rule would leave such a
 * period unsaveable. One function rather than the same expression twice, so create and update
 * cannot disagree about what a valid cutoff is.
 */
internal fun cutoffWithinPeriod(cutoff: LocalDate, startDate: LocalDate, endDate: LocalDate): Boolean =
    !cutoff.isBefore(startDate) && !cutoff.isAfter(endDate)
