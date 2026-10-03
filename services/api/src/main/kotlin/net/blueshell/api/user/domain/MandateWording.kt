package net.blueshell.api.user.domain

/**
 * The wording a member agrees to when they authorise incasso on the site. A mandate keeps the
 * version it was authorised under, so what was agreed to can be printed later. A change of
 * wording adds a version here; an old version's text is never edited.
 *
 * TWIN: `domains/user/mandateWording.ts` shows the current text. Change one, change the other.
 */
object MandateWording {
    const val CURRENT = "2026-10"

    private val texts =
        mapOf(
            CURRENT to
                "I authorise ESA Blueshell to collect my yearly contribution from this account by incasso, and my bank to pay it.",
        )

    /** The wording of [version], or null for a version that never existed. */
    fun textOf(version: String): String? = texts[version]
}
