package net.blueshell.api.platform.config.dev

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.auth.domain.UserActivationService
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Fills an empty development database with the accounts in [DevFixtures].
 *
 * Writes through the domain services rather than the repositories, so a seeded account is
 * indistinguishable from one the application made: the password is encoded by the same
 * encoder that checks it at sign-in, and the events a new user raises are raised here too.
 *
 * Seeding is a no-op once [DevFixtures.MARKER_USERNAME] exists, so restarting is safe and
 * a database somebody has been working in is never rewritten. Set
 * `app.dev-seed.enabled=false` to skip it entirely.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.dev-seed", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DevFixtureSeeder(
    private val users: UserService,
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val activations: UserActivationService,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (users.existsByUsername(DevFixtures.MARKER_USERNAME)) {
            log.info("Development fixtures are already present; leaving the database alone.")
            return
        }
        seed()
    }

    @Transactional
    fun seed() {
        val period = currentPeriod()
        val seeded = DevFixtures.ACCOUNTS.map { account -> account to createUser(account) }

        seeded.forEach { (account, user) ->
            account.membership?.let { attachMembership(user, it) }
            if (account.paid) {
                contributions.create(Contribution(user = user, contributionPeriod = period))
            }
            // Creating a user raises the event that issues an ordinary activation, because the
            // seeder is nobody. A board-created account takes the member activation instead, so
            // retire that one and issue the link a board-created account actually gets.
            if (account.boardCreated) {
                val userId = requireNotNull(user.id)
                activations.revokeOutstandingActivations(userId)
                activations.issueActivationForNewUser(userId, createdByBoard = true)
            }
        }

        log.info("Seeded {} development accounts, all with password '{}':", seeded.size, DevFixtures.DEV_PASSWORD)
        seeded.forEach { (account, _) -> log.info("  {} — {}", account.username.padEnd(18), account.note) }
    }

    private fun createUser(account: DevFixtures.Account): User =
        users.create(
            User(
                username = account.username,
                email = account.email,
                password = requireNotNull(passwordEncoder.encode(DevFixtures.DEV_PASSWORD)),
                initials = account.initials,
                firstName = account.firstName,
                lastName = account.lastName,
                // Deterministic and unique: the account list is fixed, so its index is too.
                phoneNumber = "+3161000%04d".format(DevFixtures.ACCOUNTS.indexOf(account)),
                discord = "${account.username}#0001",
                enabled = account.enabled,
                consentPrivacy = true,
                roles = mutableSetOf(account.role),
            ),
        )

    private fun attachMembership(user: User, fixture: DevFixtures.Membership) {
        val start = DevFixtures.PERIOD_START.minusYears(1)
        memberships.create(
            Membership(
                user = user,
                startDate = start,
                endDate = if (fixture.ended) DevFixtures.PERIOD_START.minusDays(1) else null,
                memberType = fixture.type,
                incasso = fixture.incasso,
            ),
        )
    }

    /** Reuse a period that already covers today rather than stacking another on top of it. */
    private fun currentPeriod(): ContributionPeriod {
        val today = LocalDate.now()
        periods.findLatest()?.let { latest ->
            if (!today.isBefore(latest.startDate) && !today.isAfter(latest.endDate)) return latest
        }
        return periods.create(
            ContributionPeriod(
                startDate = DevFixtures.PERIOD_START,
                endDate = DevFixtures.PERIOD_END,
                halfYearFee = DevFixtures.HALF_YEAR_FEE,
                fullYearFee = DevFixtures.FULL_YEAR_FEE,
                alumniFee = DevFixtures.ALUMNI_FEE,
            ),
        )
    }
}
