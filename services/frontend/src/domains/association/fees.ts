import {DateTime} from "luxon"
import type {ContributionPeriod} from "./adapters/association"

/** One thing membership can cost, priced and qualified. */
export interface Fee {
  id: string
  /** `€ 20,00`, already in the association's own currency and locale. */
  amount: string
  label: string
  /** The condition on it, where there is one. */
  note: string
}

/** The fees for a year, and the one sentence about them that is always worth reading. */
export interface FeeQuote {
  /** `2025/2026`, the academic year the amounts belong to. */
  year: string
  /** Whether today falls inside that year, which decides how the note is worded. */
  settled: boolean
  fees: Fee[]
  note: string
}

const euros = new Intl.NumberFormat("nl-NL", {style: "currency", currency: "EUR"})

/**
 * The fees as the page states them, or nothing where no period has been recorded.
 *
 * Composed here rather than in the band, so the two sentences about a price that has not been
 * set yet are stated once. `ContributionPeriodComponent` states the same rules in its own
 * markup for the signup form: two implementations of one rule, deliberately, because the form
 * is not being redesigned and neither reads the other.
 */
export function feeQuote(period: ContributionPeriod | null, now: DateTime = DateTime.now()): FeeQuote | null {
  if (!period?.startDate || !period?.endDate) return null

  const start = DateTime.fromISO(period.startDate)
  const end = DateTime.fromISO(period.endDate)
  const settled = now >= start && now <= end

  return {
    year: `${start.toFormat("yyyy")}/${end.toFormat("yyyy")}`,
    settled,
    fees: [
      {
        id: "full-year",
        amount: euros.format(period.fullYearFee),
        label: "A full year",
        note: "1 September to 31 August",
      },
      {
        id: "half-year",
        amount: euros.format(period.halfYearFee),
        label: "Half a year",
        note: "Only between February and July",
      },
      {
        id: "alumni",
        amount: euros.format(period.alumniFee),
        label: "Alumni",
        note: "For members who have finished studying",
      },
    ],
    note: settled
      ? "Fees are set each year at the General Members Meeting in September and are subject to change."
      : `These are the ${start.toFormat("yyyy")}/${end.toFormat("yyyy")} fees. This year's are set at the `
        + "General Members Meeting in September and are subject to change.",
  }
}
