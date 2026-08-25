package net.blueshell.api.platform.config.dev

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import java.time.LocalDate

/**
 * The accounts a development database is seeded with.
 *
 * Fixed rather than generated: a fixture you can name is one you can write a bug report
 * about, point a colleague at, or assert against. Randomised data makes every developer's
 * database a different database, and the state that matters here — an account that never
 * confirmed its address, a member who owes nothing — is exactly the state a generator
 * produces only by accident.
 *
 * Every account shares [DEV_PASSWORD]. These are seeded only under the `dev` profile.
 */
object DevFixtures {

    /** The password every seeded account signs in with. Dev only; never reaches another profile. */
    const val DEV_PASSWORD: String = "Blueshell1!"

    /** Marks a database as already seeded, so seeding twice is a no-op. */
    const val MARKER_USERNAME: String = "admin"

    enum class Membership(val type: MemberType, val incasso: Boolean, val ended: Boolean = false) {
        /** Pays by direct debit; the default case for most actions. */
        REGULAR_INCASSO(MemberType.REGULAR, incasso = true),
        /** Pays by transfer, so a reminder is the way to reach them. */
        REGULAR_TRANSFER(MemberType.REGULAR, incasso = false),
        /** Owes no contribution, so every contribution action must skip them. */
        HONORARY(MemberType.HONORARY, incasso = false),
        /** A lower fee tier. */
        ALUMNI(MemberType.ALUMNI, incasso = true),
        /** Membership is over; shows as Former rather than Current. */
        FORMER(MemberType.REGULAR, incasso = false, ended = true),
    }

    data class Account(
        val username: String,
        val firstName: String,
        val lastName: String,
        val role: Role,
        /** False leaves the address unconfirmed, which is what the recovery manager calls inactive. */
        val enabled: Boolean = true,
        val membership: Membership? = null,
        /** Whether a contribution is already recorded for the current period. */
        val paid: Boolean = false,
        /** Board-created accounts activate through the member email rather than the ordinary one. */
        val boardCreated: Boolean = false,
        val note: String,
    ) {
        val email: String get() = "$username@blueshell.test"
        val initials: String get() = "${firstName.first()}${lastName.first()}"
    }

    /** One account per situation worth clicking through, and no two the same. */
    val ACCOUNTS: List<Account> = listOf(
        Account(
            MARKER_USERNAME, "Ada", "Admin", Role.ADMIN,
            note = "Sign in here; reaches every management page.",
        ),
        Account(
            "board", "Bo", "Board", Role.BOARD,
            note = "The permission the user and recovery managers actually require.",
        ),
        Account(
            "treasurer", "Tess", "Treasurer", Role.TREASURER,
            note = "Contribution periods and bulk contribution actions.",
        ),
        Account(
            "committee", "Cas", "Committee", Role.COMMITTEE,
            membership = Membership.REGULAR_INCASSO,
            note = "Has a role above member, which bulk end-membership protects.",
        ),
        Account(
            "member.paid", "Mila", "Paid", Role.MEMBER,
            membership = Membership.REGULAR_INCASSO, paid = true,
            note = "Already paid, so marking paid reports them unchanged.",
        ),
        Account(
            "member.unpaid", "Milo", "Unpaid", Role.MEMBER,
            membership = Membership.REGULAR_TRANSFER,
            note = "Owes the full fee and pays by transfer, so a reminder applies.",
        ),
        Account(
            "member.honorary", "Hana", "Honorary", Role.MEMBER,
            membership = Membership.HONORARY,
            note = "Owes nothing, so every contribution action must refuse or skip them.",
        ),
        Account(
            "member.alumni", "Alex", "Alumni", Role.MEMBER,
            membership = Membership.ALUMNI,
            note = "The alumni fee tier.",
        ),
        Account(
            "member.former", "Fay", "Former", Role.MEMBER,
            membership = Membership.FORMER,
            note = "Membership has ended; shows as Former, and can be resumed.",
        ),
        Account(
            "guest.unconfirmed", "Uma", "Unconfirmed", Role.GUEST,
            enabled = false,
            note = "Signed itself up and never confirmed: takes the ordinary activation email.",
        ),
        Account(
            "member.boardcreated", "Bram", "Boardmade", Role.MEMBER,
            enabled = false, boardCreated = true, membership = Membership.REGULAR_INCASSO,
            note = "Created by the board and never activated: takes the member activation email.",
        ),
    )

    /** The period contributions are recorded against, spanning today so it is the current one. */
    val PERIOD_START: LocalDate = LocalDate.now().withDayOfYear(1)
    val PERIOD_END: LocalDate = PERIOD_START.plusYears(1).minusDays(1)
    const val FULL_YEAR_FEE: Double = 40.0
    const val HALF_YEAR_FEE: Double = 20.0
    const val ALUMNI_FEE: Double = 15.0
}
