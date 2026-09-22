package net.blueshell.api.event.domain

import net.blueshell.api.shared.seed.SeedCsv

/**
 * The committees and events a development database is filled with, under `db/seed/events`.
 *
 * Real ones, read off the association's own site by `scripts/scrape-public-events.py`: a page
 * drawing events has to cope with a title that runs long, a description written as a poster
 * caption, and the mix of members-only, sign-up and walk-in that the real months hold. The
 * `art` column names the banner a row carries, under `art/`, or is empty.
 */
object EventSeed {
    val files = SeedCsv("db/seed/events")

    const val COMMITTEES = "committees.csv"
    const val EVENTS = "events.csv"
}
