package net.blueshell.api.testsupport

import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import java.time.Instant
import java.time.LocalDate

/**
 * Real entities for a unit test, built without a database: a test names the fields it reads and
 * takes a default for the rest. A mocked entity answers only what was stubbed, so a rule that
 * reads one more field passes on a null the entity could never hold.
 */
object Entities {
    private val START: Instant = Instant.parse("2026-10-01T18:00:00Z")

    fun user(
        id: Long? = null,
        username: String = "user${id ?: 0}",
        email: String = "$username@example.com",
        firstName: String = "User",
        lastName: String = "${id ?: 0}",
        roles: Set<Role> = setOf(Role.GUEST),
        enabled: Boolean = true,
    ): User =
        User(
            username = username,
            email = email,
            password = "hashed",
            initials = firstName.take(1) + ".",
            firstName = firstName,
            lastName = lastName,
            enabled = enabled,
            roles = roles.toMutableSet(),
        ).also { it.id = id }

    /** A committee seating an account for each of [memberIds]. */
    fun committee(
        id: Long? = null,
        name: String = "Committee ${id ?: 0}",
        description: String = "A committee",
        memberIds: List<Long> = emptyList(),
    ): Committee =
        Committee(name = name, description = description).also { committee ->
            committee.id = id
            committee.replaceMembers(memberIds.map { CommitteeMember(committee = committee, user = user(id = it)) })
        }

    fun event(
        id: Long? = null,
        committee: Committee? = null,
        title: String = "Event ${id ?: 0}",
        startTime: Instant = START,
        endTime: Instant = startTime.plusSeconds(3600),
        approved: Boolean = false,
        membersOnly: Boolean = false,
        signUp: Boolean = false,
        signUpCount: Long = 0,
    ): Event =
        Event(
            committee = committee,
            title = title,
            startTime = startTime,
            endTime = endTime,
            approved = approved,
            membersOnly = membersOnly,
            signUp = signUp,
        ).also {
            it.id = id
            filledByTheDatabase(it, "signUpCount", signUpCount)
        }

    fun signUp(
        id: Long? = null,
        event: Event = event(),
        userId: Long? = null,
        guest: Guest? = null,
    ): EventSignUp = EventSignUp(event = event, userId = userId, guest = guest).also { it.id = id }

    fun question(
        id: Long? = null,
        survey: Survey = Survey(),
        type: QuestionType = QuestionType.OPEN,
        label: String = "Question ${id ?: 0}",
    ): Question = Question(idx = 0, survey = survey, type = type, label = label).also { it.id = id }

    fun file(
        id: Long? = null,
        uploader: User = user(),
        type: FileType = FileType.EVENT_BANNER,
        name: String = "file-${id ?: 0}.webp",
        path: String = "${type.directory}/$name",
        mediaType: String = "image/webp",
        renditionWidth: Int? = null,
        renditions: List<File> = emptyList(),
    ): File =
        File(
            name = name,
            path = path,
            uploader = uploader,
            mediaType = mediaType,
            type = type,
            renditionWidth = renditionWidth,
        ).also { file ->
            file.id = id
            @Suppress("UNCHECKED_CAST")
            (
                File::class.java
                    .getDeclaredField("_renditions")
                    .apply { isAccessible = true }
                    .get(file) as MutableList<File>
            ).addAll(renditions)
        }

    fun banner(
        event: Event,
        file: File = file(),
    ): EventBanner = EventBanner(event = event, file = file)

    fun cohort(
        id: Long? = null,
        system: String = TargetSystem.BREVO.name,
        kind: CohortKind = CohortKind.LIST,
        label: String = "Cohort ${id ?: 0}",
        folder: String? = null,
        subjectId: Long? = null,
        externalId: String? = null,
    ): Cohort =
        Cohort(system = system, kind = kind, label = label, folder = folder, subjectId = subjectId, externalId = externalId)
            .also { it.id = id }

    fun cohortSubject(
        id: Long? = null,
        type: CohortSubjectType = CohortSubjectType.COMMITTEE_MEMBERS,
        label: String = "Subject ${id ?: 0}",
    ): CohortSubject = CohortSubject(type = type, label = label).also { it.id = id }

    fun recoveryToken(
        id: Long? = null,
        user: User = user(),
        type: TokenPurpose = TokenPurpose.PASSWORD_RESET,
        expiresAt: Instant = START.plusSeconds(3600),
    ): RecoveryToken =
        RecoveryToken(user = user, type = type, selector = "selector", verifierHash = "hash", expiresAt = expiresAt).also {
            it.id =
                id
        }

    fun membership(
        id: Long? = null,
        user: User = user(),
        startDate: LocalDate = LocalDate.of(2026, 9, 1),
        endDate: LocalDate? = null,
    ): Membership = Membership(user = user, startDate = startDate, endDate = endDate).also { it.id = id }

    fun period(
        id: Long? = null,
        startDate: LocalDate = LocalDate.of(2026, 9, 1),
        endDate: LocalDate = startDate.plusYears(1).minusDays(1),
    ): ContributionPeriod =
        ContributionPeriod(startDate = startDate, endDate = endDate, halfYearCutoffDate = startDate.plusMonths(6)).also { it.id = id }

    fun contribution(
        id: Contribution.Id = Contribution.Id(),
        user: User = user(),
        period: ContributionPeriod = period(),
    ): Contribution = Contribution(id = id, user = user, contributionPeriod = period)

    fun board(
        id: Long? = null,
        number: Int = 50,
    ): Board = Board(number = number, candidate = "Candidate board", startDate = LocalDate.of(2026, 9, 1)).also { it.id = id }

    fun boardMember(
        id: Long? = null,
        board: Board = board(),
        user: User? = user(),
        role: String = "Chair",
    ): BoardMember = BoardMember(board = board, user = user, role = role, startDate = board.startDate).also { it.id = id }

    fun team(
        id: Long? = null,
        name: String = "Team ${id ?: 0}",
    ): Team = Team(name = name).also { it.id = id }

    fun teamSeason(
        team: Team = team(),
        game: String = "valorant",
    ): TeamSeason = TeamSeason(team = team, game = game, season = Season("2026", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31)))

    fun address(
        id: Long? = null,
        user: User = user(),
    ): Address = Address(user = user).also { it.id = id }

    fun memberProfile(
        id: Long? = null,
        user: User = user(),
        conditionsAcceptedAt: Instant? = START,
    ): MemberProfile =
        MemberProfile(user = user, bhv = false, ehbo = false, conditionsAcceptedAt = conditionsAcceptedAt).also { it.id = id }

    fun guest(accessToken: String = "GUEST-TOKEN"): Guest =
        Guest.withRawToken(name = "Guest", discord = "guest#0001", email = "guest@example.com", accessToken = accessToken)

    fun email(id: Long? = null): Email = Email().also { it.id = id }

    fun survey(): Survey = Survey()

    fun telemetry(
        id: Long? = null,
        url: String = "https://example.com",
    ): Telemetry = Telemetry(platform = PlatformType.FACEBOOK, url = url).also { it.id = id }

    fun reminder(
        id: Long? = null,
        user: User = user(),
        period: ContributionPeriod = period(),
    ): ContributionReminder = ContributionReminder(user = user, contributionPeriod = period).also { it.id = id }

    fun incassoNotification(
        id: Long? = null,
        user: User = user(),
        period: ContributionPeriod = period(),
    ): IncassoNotification =
        IncassoNotification(
            user = user,
            contributionPeriod = period,
            feeType = BulkFeeType.FULL_YEAR_FEE,
            amount = 45.0,
            debitDate = period.startDate.plusMonths(1),
        ).also { it.id = id }

    /** A column the database fills and the entity only reads, such as a count a trigger keeps. */
    private fun filledByTheDatabase(
        entity: Any,
        field: String,
        value: Any,
    ) {
        entity.javaClass
            .getDeclaredField(field)
            .apply { isAccessible = true }
            .set(entity, value)
    }
}
