package net.blueshell.api.platform.config

import com.github.javafaker.Faker
import net.blueshell.api.domain.auth.application.RecoveryService
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.survey.application.SurveyService
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.sql.Date
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Component
@Profile("dev")
class DatabaseSeeder(
    private val userService: UserService,
    private val addressService: AddressService,
    private val committeeService: CommitteeService,
    private val committeeMemberService: CommitteeMemberService,
    private val eventService: EventService,
    private val eventSignUpService: EventSignUpService,
    private val membershipService: MembershipService,
    private val contributionPeriodService: ContributionPeriodService,
    private val contributionService: ContributionService,
    private val recoveryService: RecoveryService,
    private val surveyService: SurveyService,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    private val faker = Faker(Locale.ENGLISH)
    private val createdUsers: MutableMap<String, User> = linkedMapOf()
    private val rnd = Random(42)

    // Track committee sizes & memberships to enforce max 6 members
    private val committeeMemberCounts: MutableMap<Long, Int> = mutableMapOf()
    private val committeeMemberships: MutableSet<String> = mutableSetOf()

    // key: contributionPeriodId -> set of userIds who have a contribution in that period
    private val contributionsByPeriod: MutableMap<Long, MutableSet<Long>> = mutableMapOf()

    // ✅ Correct signature: CommandLineRunner.run(vararg args: String!)
    override fun run(vararg args: String) {
        if (userService.existsByUsername("board.user")) {
            log.info("DEV database seeding skipped: already seeded.")
            return
        }

        val today = LocalDate.now()

        val currentPeriod = createContributionPeriod(
            start = today.withMonth(8).withDayOfMonth(3),
            end = today.withMonth(8).withDayOfMonth(2).plusYears(1)
        )
        val previousPeriod = createContributionPeriod(
            start = today.minusYears(1).withMonth(8).withDayOfMonth(3),
            end = today.withMonth(8).withDayOfMonth(2)
        )

        val previousPeriodMembers = mutableListOf<User>()
        val currentPeriodMembers = mutableListOf<User>()

        // ---- Committees ----
        val testCommittee = createCommittee("Test Committee", "Committee for seeded events and testing.")
        val activitiesCommittee = createCommittee("Activities Committee", "Organises general activities for members.")
        val sportsCommittee = createCommittee("Sports Committee", "Handles sports-related events and tournaments.")
        val itCommittee = createCommittee("IT Committee", "Maintains systems, website and IT tooling.")
        val socialCommittee = createCommittee("Social Committee", "Organises social gatherings and borrels.")

        val allCommittees = listOf(testCommittee, activitiesCommittee, sportsCommittee, itCommittee, socialCommittee)

        // ---- Reference users ----
        val boardUser = createUserWithRole(
            username = "board.user",
            firstName = "Board",
            lastName = "User",
            email = "board.user@esa-blueshell.nl",
            role = Role.BOARD,
            includeAddress = true
        )
        val committeeUser = createUserWithRole(
            username = "committee.user",
            firstName = "Committee",
            lastName = "User",
            email = "committee.user@esa-blueshell.nl",
            role = Role.COMMITTEE,
            includeAddress = true
        )
        val memberUser = createUserWithRole(
            username = "member.user",
            firstName = "Member",
            lastName = "User",
            email = "member.user@esa-blueshell.nl",
            role = Role.GUEST,
            includeAddress = true
        )
        val normalUser = createUserWithRole(
            username = "normal.user",
            firstName = "Normal",
            lastName = "User",
            email = "normal.user@esa-blueshell.nl",
            role = Role.GUEST,
            includeAddress = true
        )
        val guestInactive = createInactiveUser(
            username = "guest.inactive",
            firstName = "Guest",
            lastName = "Inactive",
            email = "guest.inactive@esa-blueshell.nl",
            includeAddress = false
        )

        createdUsers[boardUser.username] = boardUser
        createdUsers[committeeUser.username] = committeeUser
        createdUsers[memberUser.username] = memberUser
        createdUsers[normalUser.username] = normalUser
        createdUsers[guestInactive.username] = guestInactive

        // Attach committee members
        createCommitteeMember(committeeUser, testCommittee, "Chair")
        createCommitteeMember(boardUser, testCommittee, "Board Liaison")

        // Give core reference users memberships
        createMembership(previousPeriod, boardUser).also { previousPeriodMembers += boardUser }
        createMembership(currentPeriod, boardUser).also { currentPeriodMembers += boardUser }

        createMembership(previousPeriod, committeeUser).also { previousPeriodMembers += committeeUser }
        createMembership(currentPeriod, committeeUser).also { currentPeriodMembers += committeeUser }

        createMembership(previousPeriod, memberUser).also { previousPeriodMembers += memberUser }
        createMembership(currentPeriod, memberUser).also { currentPeriodMembers += memberUser }

        createContribution(boardUser, currentPeriod)
        createContribution(committeeUser, currentPeriod)
        createContribution(memberUser, currentPeriod)

        // ---- Bulk users ----
        val memberPool = mutableListOf(boardUser, committeeUser, memberUser)
        val guestPool = mutableListOf(normalUser)

        repeat(60) {
            val u = createRandomUser(Role.GUEST, includeAddress = true)
            createdUsers[u.username] = u

            createMembership(previousPeriod, u).also { previousPeriodMembers += u }
            createMembership(currentPeriod, u).also { currentPeriodMembers += u }

            if (rnd.nextDouble() < 0.7) createContribution(u, currentPeriod)

            memberPool += u
        }

        repeat(10) {
            val u = createRandomUser(Role.GUEST, includeAddress = true)
            createdUsers[u.username] = u
            createMembership(previousPeriod, u).also { previousPeriodMembers += u }
        }

        repeat(10) {
            val u = createRandomUser(Role.GUEST, includeAddress = true)
            createdUsers[u.username] = u
            createMembership(currentPeriod, u).also { currentPeriodMembers += u }
            memberPool += u
        }

        repeat(40) {
            val u = createRandomUser(Role.GUEST, includeAddress = true)
            createdUsers[u.username] = u
            guestPool += u
        }

        ensureContributionRatios(previousPeriod, currentPeriod, previousPeriodMembers, currentPeriodMembers)

        seedCommitteeMembers(allCommittees, memberPool)

        // ✅ Kotlin property access for recovery tokens
        createdUsers.values.forEach { u ->
            if (u.enabled) {
                u.recoveryTokens.forEach { rt: RecoveryToken ->
                    rt.consumedAt = Instant.now()
                }
            }
        }

        // ---- Events ----
        val eventsConfig = listOf(
            EventConfig(
                "Past Approved Members-Only with Sign-Up",
                approved = true,
                membersOnly = true,
                signUp = true,
                startOffset = -30,
                endOffset = -29,
                withForm = true
            ),
            EventConfig(
                "Past Approved Members-Only without Sign-Up",
                approved = true,
                membersOnly = true,
                signUp = false,
                startOffset = -25,
                endOffset = -24,
                withForm = false
            ),
            EventConfig(
                "Past Approved Public with Sign-Up",
                approved = true,
                membersOnly = false,
                signUp = true,
                startOffset = -20,
                endOffset = -19,
                withForm = true
            ),
            EventConfig(
                "Past Approved Public without Sign-Up",
                approved = true,
                membersOnly = false,
                signUp = false,
                startOffset = -15,
                endOffset = -14,
                withForm = false
            ),
            EventConfig(
                "Past Not-Approved Members-Only with Sign-Up",
                approved = false,
                membersOnly = true,
                signUp = true,
                startOffset = -10,
                endOffset = -9,
                withForm = true
            ),
            EventConfig(
                "Past Not-Approved Members-Only without Sign-Up",
                approved = false,
                membersOnly = true,
                signUp = false,
                startOffset = -8,
                endOffset = -7,
                withForm = false
            ),
            EventConfig(
                "Past Not-Approved Public with Sign-Up",
                approved = false,
                membersOnly = false,
                signUp = true,
                startOffset = -6,
                endOffset = -5,
                withForm = true
            ),
            EventConfig(
                "Past Not-Approved Public without Sign-Up",
                approved = false,
                membersOnly = false,
                signUp = false,
                startOffset = -4,
                endOffset = -3,
                withForm = false
            ),
            EventConfig(
                "Future Approved Members-Only with Sign-Up",
                approved = true,
                membersOnly = true,
                signUp = true,
                startOffset = 5,
                endOffset = 6,
                withForm = true
            ),
            EventConfig(
                "Future Approved Members-Only without Sign-Up",
                approved = true,
                membersOnly = true,
                signUp = false,
                startOffset = 7,
                endOffset = 8,
                withForm = false
            ),
            EventConfig(
                "Future Approved Public with Sign-Up",
                approved = true,
                membersOnly = false,
                signUp = true,
                startOffset = 10,
                endOffset = 11,
                withForm = true
            ),
            EventConfig(
                "Future Approved Public without Sign-Up",
                approved = true,
                membersOnly = false,
                signUp = false,
                startOffset = 12,
                endOffset = 13,
                withForm = false
            ),
            EventConfig(
                "Future Not-Approved Members-Only with Sign-Up",
                approved = false,
                membersOnly = true,
                signUp = true,
                startOffset = 15,
                endOffset = 16,
                withForm = true
            ),
            EventConfig(
                "Future Not-Approved Members-Only without Sign-Up",
                approved = false,
                membersOnly = true,
                signUp = false,
                startOffset = 17,
                endOffset = 18,
                withForm = false
            ),
            EventConfig(
                "Future Not-Approved Public with Sign-Up",
                approved = false,
                membersOnly = false,
                signUp = true,
                startOffset = 20,
                endOffset = 21,
                withForm = true
            ),
            EventConfig(
                "Future Not-Approved Public without Sign-Up",
                approved = false,
                membersOnly = false,
                signUp = false,
                startOffset = 22,
                endOffset = 23,
                withForm = false
            ),
            EventConfig(
                "Event with Sign-Up Form",
                approved = true,
                membersOnly = false,
                signUp = true,
                startOffset = 25,
                endOffset = 26,
                withForm = true
            ),
        )

        val events = mutableListOf<Event>()
        for ((committeeIndex, ec) in eventsConfig.withIndex()) {
            val hostingCommittee =
                if (committeeIndex < allCommittees.size) allCommittees[committeeIndex]
                else allCommittees[rnd.nextInt(allCommittees.size)]

            events += createEvent(
                committee = hostingCommittee,
                title = ec.title,
                approved = ec.approved,
                membersOnly = ec.membersOnly,
                signUp = ec.signUp,
                startDaysOffset = ec.startOffset,
                endDaysOffset = ec.endOffset,
                withForm = ec.withForm
            )
        }

        var previouslyApproved = createEvent(
            committee = testCommittee,
            title = "Previously Approved Event",
            approved = true,
            membersOnly = false,
            signUp = true,
            startDaysOffset = 30,
            endDaysOffset = 31,
            withForm = true
        )
        events += previouslyApproved

        // Seed signups
        for (e in events) {
            if (e.signUp) {
                createEventSignUpWithAnswers(memberUser, e)
                if (!e.membersOnly) createEventSignUpWithAnswers(normalUser, e)
            }
        }

        for (e in events) if (e.signUp) seedSignUps(e, memberPool, guestPool)
        for (e in events) if (e.signUp && !e.membersOnly) seedGuestSignUps(e)

        createEventSignUpWithAnswers(boardUser, previouslyApproved)
        createEventSignUpWithAnswers(committeeUser, previouslyApproved)

        // Revoke approval
        previouslyApproved = requireNotNull(eventService.findById(previouslyApproved.id!!)) { "Event not found" }
        previouslyApproved.approved = false
        eventService.update(previouslyApproved)

        deactivateRandomUsers(
            count = 10,
            allUsers = createdUsers.values,
            excludedUsernames = setOf(
                boardUser.username,
                committeeUser.username,
                memberUser.username,
                normalUser.username,
                guestInactive.username
            )
        )

        log.info("Database seeding completed.")
        log.info("Created users ({}): {}", createdUsers.size, createdUsers.keys.joinToString(", "))
        log.info("Created {} events", events.size)
    }

    // ---- Helpers ----

    private fun createRandomUser(baseRole: Role, includeAddress: Boolean): User {
        val first = safeName(faker.name().firstName())
        val last = safeName(faker.name().lastName())
        val base = "$first.$last".lowercase()
        val username = uniqueUsername(base)
        val email = "$username@esa-blueshell.nl"

        var u = createUserWithRole(username, first, last, email, baseRole, includeAddress)

        if (!includeAddress && rnd.nextBoolean()) {
            val addr = createAddressEntity()
            u.address = addr
            u = requireNotNull(userService.update(u)) { "User update returned null" }
        }

        return u
    }

    private fun safeName(s: String): String =
        s.replace(Regex("[^A-Za-z0-9]"), "")

    private fun uniqueUsername(base: String): String {
        var u = base
        var i = 1
        while (userService.existsByUsername(u)) {
            u = "$base.${i++}"
        }
        return u
    }

    private fun createMembership(period: ContributionPeriod, user: User): Membership {
        val membership = Membership().apply {
            startDate = period.startDate
            endDate = period.endDate
            memberType = MemberType.REGULAR
            this.user = user
        }
        return requireNotNull(membershipService.create(membership)) { "Membership create returned null" }
    }

    private fun createUserWithRole(
        username: String,
        firstName: String,
        lastName: String,
        email: String,
        role: Role,
        includeAddress: Boolean
    ): User {
        val user = User().apply {
            this.username = username
            password = passwordEncoder.encode("password")
            this.firstName = firstName
            this.lastName = lastName
            this.email = email
            enabled = true

            // 🔁 If you have inherited roles, replace this with your helper:
            roles = setOf(role) as MutableSet<Role>

            newsletter = false
            dateOfBirth = Date.valueOf(LocalDate.of(1995, 1, 1))
            consentPrivacy = true
            consentGdpr = true
            if (includeAddress) address = createAddressEntity()
        }
        return requireNotNull(userService.create(user)) { "User create returned null" }
    }

    private fun createInactiveUser(
        username: String,
        firstName: String,
        lastName: String,
        email: String,
        includeAddress: Boolean
    ): User {
        val user = User(
        ).apply {
            this.username = username
            password = passwordEncoder.encode("temporary")
            this.firstName = firstName
            this.lastName = lastName
            this.email = email
            enabled = false
            roles = setOf(Role.GUEST) as MutableSet<Role>
            newsletter = false
            dateOfBirth = Date.valueOf(LocalDate.of(1995, 1, 1))
            consentPrivacy = true
            consentGdpr = true
            if (includeAddress) address = createAddressEntity()
        }
        return requireNotNull(userService.create(user)) { "User create returned null" }
    }

    private fun createAddressEntity(): Address =
        Address().apply {
            country = "Netherlands"
            city = "Amsterdam"
            street = "Test Street"
            houseNumber = "123"
            zipCode = "1234 AB"
        }

    private fun createCommittee(name: String, description: String): Committee {
        val committee = Committee().apply {
            this.name = name
            this.description = description
        }
        return requireNotNull(committeeService.create(committee)) { "Committee create returned null" }
    }

    private fun createCommitteeMember(user: User?, committee: Committee?, role: String?): CommitteeMember? {
        if (committee == null || user == null) return null

        val key = "${committee.id}:${user.id}"
        if (key in committeeMemberships) return null

        val currentCount = committeeMemberCounts[committee.id] ?: 0
        if (currentCount >= 6) {
            log.debug("Committee '{}' already has max members (6), skipping {}", committee.name, user.username)
            return null
        }

        val member = CommitteeMember().apply {
            this.user = user
            this.committee = committee
            this.role = role
        }

        val created = committeeMemberService.create(member)
        committeeMemberCounts[committee.id!!] = currentCount + 1
        committeeMemberships += key

        return created
    }

    private fun createContributionPeriod(start: LocalDate, end: LocalDate): ContributionPeriod {
        val period = ContributionPeriod().apply {
            startDate = start
            endDate = end
            halfYearFee = 10.0
            fullYearFee = 18.0
            alumniFee = 5.0
        }
        return requireNotNull(contributionPeriodService.create(period)) { "ContributionPeriod create returned null" }
    }

    private fun createContribution(user: User, period: ContributionPeriod): Contribution {
        val c = Contribution().apply {
            this.user = user
            this.contributionPeriod = period
        }
        val created = requireNotNull(contributionService.create(c)) { "Contribution create returned null" }

        contributionsByPeriod.getOrPut(period.id!!) { mutableSetOf() }.add(user.id!!)
        return created
    }

    private fun createEvent(
        committee: Committee,
        title: String,
        approved: Boolean,
        membersOnly: Boolean,
        signUp: Boolean,
        startDaysOffset: Int,
        endDaysOffset: Int,
        withForm: Boolean
    ): Event {
        val survey: Survey? =
            if (signUp && withForm) createSurveyWithAllQuestionTypes() else null

        val event = Event().apply {
            this.committee = committee
            this.title = title
            description = "Description for $title"
            location = "Test Location"
            startTime = Instant.now().plus(startDaysOffset.toLong(), ChronoUnit.DAYS)
            endTime = Instant.now().plus(endDaysOffset.toLong(), ChronoUnit.DAYS)
            this.approved = approved
            this.membersOnly = membersOnly
            this.signUp = signUp
            memberPrice = 10.0
            publicPrice = 15.0
            signUpForm = survey
        }

        return requireNotNull(eventService.create(event)) { "Event create returned null" }
    }

    private fun findExistingSignUp(eventId: Long, userId: Long): EventSignUp? =
        eventSignUpService.findByEventId(eventId).firstOrNull { it.userId == userId }

    private fun createEventSignUpWithAnswers(user: User, event: Event): EventSignUp? {
        findExistingSignUp(event.id!!, user.id!!)?.let { return it }

        val signUp = EventSignUp().apply {
            this.event = event
            this.user = user

            val form = event.signUpForm
            if (event.signUp && form?.questions != null) {
                form.questions.map { q -> createAnswerForQuestion(q) }.toMutableSet()
            } else {
                mutableSetOf()
            }
        }

        return try {
            eventSignUpService.create(signUp)
        } catch (ex: DataIntegrityViolationException) {
            log.warn(
                "Sign-up already exists for eventId={}, userId={}, skipping duplicate insert.",
                event.id, user.id
            )
            findExistingSignUp(event.id!!, user.id!!) ?: throw ex
        }
    }

    private fun seedSignUps(event: Event, memberPool: List<User>, guestPool: List<User>) {
        val candidates = buildList {
            addAll(memberPool)
            if (!event.membersOnly) addAll(guestPool)
        }.toMutableList()

        val existingUserIds = eventSignUpService.findByEventId(event.id!!)
            .mapNotNull { it.userId }
            .toMutableSet()

        candidates.shuffle(rnd)

        val maxDesired = min(candidates.size, rnd.nextInt(20, 45))
        var added = 0

        for (u in candidates) {
            if (added >= maxDesired) break
            if (u.id in existingUserIds) continue
            createEventSignUpWithAnswers(u, event)
            existingUserIds += u.id!!
            added++
        }
    }

    private fun createSurveyWithAllQuestionTypes(): Survey {
        val survey = Survey()
        val questions = linkedSetOf<Question>()

        questions += buildQuestion(
            1L,
            QuestionType.DESCRIPTION,
            "Important information:",
            emptyList<String>() as MutableList<String>
        )
        questions += buildQuestion(
            2L,
            QuestionType.RADIO,
            "Please select one option:",
            DEFAULT_RADIO_CHOICES.toMutableList()
        )
        questions += buildQuestion(
            3L,
            QuestionType.CHECKBOX,
            "Select all that apply:",
            DEFAULT_CHECKBOX_CHOICES.toMutableList()
        )
        questions += buildQuestion(
            4L,
            QuestionType.OPEN,
            "What are your thoughts?",
            emptyList<String>() as MutableList<String>
        )

        questions.forEach { it.survey = survey }

        return survey
    }

    private fun buildQuestion(
        idx: Long,
        type: QuestionType,
        label: String,
        choiceLabels: MutableList<String>
    ): Question =
        Question().apply {
            this.idx = idx
            this.type = type
            this.label = label
            this.choiceLabels = choiceLabels
        }

    private fun createAnswerForQuestion(q: Question): Answer {
        val a = Answer().apply {
            this.question = q
        }

        when (q.type) {
            QuestionType.OPEN -> a.textResponse = "Sample answer text"
            QuestionType.RADIO -> {
                val n = q.choiceLabels?.size ?: 0
                val selections = MutableList(max(n, 1)) { false }
                if (n > 0) selections[0] = true
                a.optionSelections = selections
            }

            QuestionType.CHECKBOX -> {
                val n = q.choiceLabels?.size ?: 0
                val selections = MutableList(max(n, 1)) { false }
                if (n > 0) selections[0] = true
                if (n > 2) selections[2] = true
                a.optionSelections = selections
            }

            QuestionType.DESCRIPTION -> Unit
        }
        return a
    }

    private fun buildRandomGuest(): Guest =
        Guest().apply {
            name = faker.name().fullName()
            discord = "${faker.name().username()}#${faker.number().numberBetween(1000, 9999)}"
            email = faker.internet().emailAddress()
            phoneNumber = faker.phoneNumber().phoneNumber()
            accessToken = UUID.randomUUID().toString()
        }

    private fun createGuestEventSignUpWithAnswers(guest: Guest, event: Event): EventSignUp {
        val signUp = EventSignUp().apply {
            this.event = event
            user = null
            this.guest = guest

            val form = event.signUpForm
            if (event.signUp && form?.questions != null) {
                form.questions.map { q -> createAnswerForQuestion(q) }.toMutableSet()
            } else {
                mutableSetOf()
            }
        }
        return eventSignUpService.create(signUp)
    }

    private fun seedGuestSignUps(event: Event) {
        if (event.membersOnly || !event.signUp) return
        val guestCount = rnd.nextInt(5, 21)
        repeat(guestCount) {
            createGuestEventSignUpWithAnswers(buildRandomGuest(), event)
        }
    }

    private fun ensureContributionRatios(
        previousPeriod: ContributionPeriod,
        currentPeriod: ContributionPeriod,
        previousMembers: List<User>,
        currentMembers: List<User>
    ) {
        enforceContributionRatioForPeriod(previousPeriod, previousMembers, 0.75)
        enforceContributionRatioForPeriod(currentPeriod, currentMembers, 0.60)
    }

    private fun enforceContributionRatioForPeriod(
        period: ContributionPeriod,
        members: List<User>,
        ratio: Double
    ) {
        if (members.isEmpty()) return

        val distinctMembers = members.distinctBy { it.id }
        val total = distinctMembers.size
        if (total == 0) return

        val desiredPaid = kotlin.math.round(total * ratio).toInt()
        val paidUserIds = contributionsByPeriod[period.id] ?: mutableSetOf()

        val currentPaid = paidUserIds.size
        if (currentPaid >= desiredPaid) return

        val unpaid = distinctMembers.filter { it.id !in paidUserIds }.toMutableList()
        unpaid.shuffle(rnd)

        val needed = min(desiredPaid - currentPaid, unpaid.size)
        repeat(needed) { idx ->
            createContribution(unpaid[idx], period)
        }
    }

    private fun seedCommitteeMembers(committees: List<Committee>, memberPool: List<User>) {
        if (committees.isEmpty()) return

        val coreUsernames = setOf("board.user", "committee.user", "member.user", "normal.user", "guest.inactive")
        val candidates = memberPool
            .filter { it.username !in coreUsernames }
            .distinctBy { it.id }
            .toMutableList()
        candidates.shuffle(rnd)

        val iterator = candidates.iterator()

        for (committee in committees) {
            val currentCount = committeeMemberCounts[committee.id] ?: 0
            val remainingSlots = 6 - currentCount
            if (remainingSlots <= 0) continue

            val desiredTotal = rnd.nextInt(3, 7)
            var targetAdditional = max(0, desiredTotal - currentCount)
            targetAdditional = min(targetAdditional, remainingSlots)

            repeat(targetAdditional) { i ->
                if (!iterator.hasNext()) return
                val candidate = iterator.next()
                val role = pickRoleForPosition(currentCount + i)
                createCommitteeMember(candidate, committee, role)
            }
        }
    }

    private fun pickRoleForPosition(position: Int): String =
        when (position) {
            0 -> "Chair"
            1 -> "Secretary"
            2 -> "Treasurer"
            3 -> "Event Manager"
            4 -> "PR Officer"
            else -> "Member"
        }

    private fun deactivateRandomUsers(
        count: Int,
        allUsers: Collection<User>,
        excludedUsernames: Set<String>
    ) {
        val candidates = allUsers
            .filter { it.enabled }
            .filter { it.username !in excludedUsernames }
            .toMutableList()

        if (candidates.isEmpty()) return

        candidates.shuffle(rnd)
        val limit = min(count, candidates.size)

        repeat(limit) { idx ->
            val u = requireNotNull(userService.findById(candidates[idx].id!!)) { "User not found" }
            u.enabled = false
            val updated = requireNotNull(userService.update(u)) { "User update returned null" }
            createdUsers[updated.username] = updated
        }
    }

    private data class EventConfig(
        val title: String,
        val approved: Boolean,
        val membersOnly: Boolean,
        val signUp: Boolean,
        val startOffset: Int,
        val endOffset: Int,
        val withForm: Boolean
    )

    companion object {
        private val log = LoggerFactory.getLogger(DatabaseSeeder::class.java)

        private val DEFAULT_RADIO_CHOICES = mutableListOf("Option A", "Option B", "Option C", "Option D")
        private val DEFAULT_CHECKBOX_CHOICES = mutableListOf("Choice 1", "Choice 2", "Choice 3", "Choice 4", "Choice 5")
    }
}
