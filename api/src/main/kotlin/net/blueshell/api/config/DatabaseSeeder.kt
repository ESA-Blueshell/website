package net.blueshell.api.config

import com.github.javafaker.Faker
import net.blueshell.api.common.enums.MemberType
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.common.enums.Role
import net.blueshell.api.model.Address
import net.blueshell.api.model.Membership
import net.blueshell.api.model.RecoveryToken
import net.blueshell.api.model.User
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.api.model.event.Event
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.model.event.Guest
import net.blueshell.api.model.survey.Answer
import net.blueshell.api.model.survey.Question
import net.blueshell.api.model.survey.Survey
import net.blueshell.api.service.*
import net.blueshell.api.service.contribution.ContributionPeriodService
import net.blueshell.api.service.contribution.ContributionService
import net.blueshell.api.service.event.EventService
import net.blueshell.api.service.event.EventSignUpService
import net.blueshell.api.service.survey.SurveyService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.lang.Boolean
import java.lang.String
import java.sql.Date
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.List
import java.util.Set
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.Any
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableCollection
import kotlin.collections.MutableIterator
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.contains
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.mutableListOf
import kotlin.collections.mutableSetOf
import kotlin.math.max
import kotlin.math.min
import kotlin.plus
import kotlin.text.lowercase
import kotlin.text.replace
import kotlin.text.toRegex
import kotlin.toString

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
    private val createdUsers: MutableMap<String?, User?> = LinkedHashMap<String?, User?>()
    private val rnd = Random(42)

    // Track committee sizes & memberships to enforce max 6 members
    private val committeeMemberCounts: MutableMap<Long?, Int?> = HashMap<Long?, Int?>()
    private val committeeMemberships: MutableSet<String?> = HashSet<String?>()

    // Track contributions per period to enforce contribution ratios
    // key: contributionPeriodId -> set of userIds who have a contribution in that period
    private val contributionsByPeriod: MutableMap<Long?, MutableSet<Long?>> = HashMap<Long?, MutableSet<Long?>>()

    override fun run(vararg args: String?) {
        if (userService.existsByUsername("board.user")) {
            DatabaseSeeder.log.info("DEV database seeding skipped: already seeded.")
            return
        }

        // ---- Contribution periods (mirrors ContributionPeriodController) ----
        val today = LocalDate.now()
        val currentPeriod = createContributionPeriod(
            today.withMonth(8).withDayOfMonth(3),
            today.withMonth(8).withDayOfMonth(2).plusYears(1)
        )
        val previousPeriod = createContributionPeriod(
            today.minusYears(1).withMonth(8).withDayOfMonth(3),
            today.withMonth(8).withDayOfMonth(2)
        )

        // Track which users are members per period so we can enforce ratios later
        val previousPeriodMembers = ArrayList<User?>()
        val currentPeriodMembers = ArrayList<User?>()

        // ---- Committees ----
        val testCommittee = createCommittee("Test Committee", "Committee for seeded events and testing.")
        val activitiesCommittee = createCommittee("Activities Committee", "Organises general activities for members.")
        val sportsCommittee = createCommittee("Sports Committee", "Handles sports-related events and tournaments.")
        val itCommittee = createCommittee("IT Committee", "Maintains systems, website and IT tooling.")
        val socialCommittee = createCommittee("Social Committee", "Organises social gatherings and borrels.")

        val allCommittees =
            List.of<Committee?>(testCommittee, activitiesCommittee, sportsCommittee, itCommittee, socialCommittee)

        // ---- Reference users (mirrors AuthenticationController / UserController permissions expectations) ----
        val boardUser = createUserWithRole(
            "board.user",
            "Board",
            "User",
            "board.user@esa-blueshell.nl",
            Role.BOARD,
            true
        )
        val committeeUser = createUserWithRole(
            "committee.user",
            "Committee",
            "User",
            "committee.user@esa-blueshell.nl",
            Role.COMMITTEE,
            true
        )
        val memberUser = createUserWithRole(
            "member.user",
            "Member",
            "User",
            "member.user@esa-blueshell.nl",
            Role.GUEST,
            true
        )
        val normalUser = createUserWithRole(
            "normal.user",
            "Normal",
            "User",
            "normal.user@esa-blueshell.nl",
            Role.GUEST,
            true
        )
        val guestInactive = createInactiveUser(
            "guest.inactive",
            "Guest",
            "Inactive",
            "guest.inactive@esa-blueshell.nl",
            false
        )

        createdUsers.put(boardUser.username, boardUser)
        createdUsers.put(committeeUser.username, committeeUser)
        createdUsers.put(memberUser.username, memberUser)
        createdUsers.put(normalUser.username, normalUser)
        createdUsers.put(guestInactive.username, guestInactive)

        // Attach committee members (mirrors CommitteeController)
        createCommitteeMember(committeeUser, testCommittee, "Chair")
        createCommitteeMember(boardUser, testCommittee, "Board Liaison")

        // Give core reference users a real membership footprint
        createMembership(previousPeriod, boardUser)
        previousPeriodMembers.add(boardUser)
        createMembership(currentPeriod, boardUser)
        currentPeriodMembers.add(boardUser)

        createMembership(previousPeriod, committeeUser)
        previousPeriodMembers.add(committeeUser)
        createMembership(currentPeriod, committeeUser)
        currentPeriodMembers.add(committeeUser)

        createMembership(previousPeriod, memberUser)
        previousPeriodMembers.add(memberUser)
        createMembership(currentPeriod, memberUser)
        currentPeriodMembers.add(memberUser)

        createContribution(boardUser, currentPeriod)
        createContribution(committeeUser, currentPeriod)
        createContribution(memberUser, currentPeriod)

        // ---- Bulk users to make everything feel real ----
        val memberPool = ArrayList<User?>(List.of<User?>(boardUser, committeeUser, memberUser))
        val guestPool = ArrayList<User?>(List.of<User?>(normalUser))

        // Extra current members (paid/unpaid mix)
        val extraMembers = 60
        for (i in 0..<extraMembers) {
            val u = createRandomUser(Role.GUEST, true)
            createdUsers.put(u.username, u)

            // Give them previous+current memberships
            createMembership(previousPeriod, u)
            previousPeriodMembers.add(u)
            createMembership(currentPeriod, u)
            currentPeriodMembers.add(u)

            // Roughly 70% paid (we'll fix aggregate ratios later)
            if (rnd.nextDouble() < 0.7) {
                createContribution(u, currentPeriod)
            }

            memberPool.add(u)
        }

        // Former members (membership ended last period)
        val formerMembers = 10
        for (i in 0..<formerMembers) {
            val u = createRandomUser(Role.GUEST, true)
            createdUsers.put(u.username, u)
            createMembership(previousPeriod, u)
            previousPeriodMembers.add(u)
            // No current membership
            // Keep them out of memberPool to reflect "former".
        }

        // Unpaid members (current membership but no contribution)
        val unpaidMembers = 10
        for (i in 0..<unpaidMembers) {
            val u = createRandomUser(Role.GUEST, true)
            createdUsers.put(u.username, u)
            createMembership(currentPeriod, u)
            currentPeriodMembers.add(u)
            memberPool.add(u)
        }

        // Guests (no membership)
        val guests = 40
        for (i in 0..<guests) {
            val u = createRandomUser(Role.GUEST, true)
            createdUsers.put(u.username, u)
            guestPool.add(u)
        }

        // Ensure at least 75% of past members and 60% of current members paid their contribution
        ensureContributionRatios(previousPeriod, currentPeriod, previousPeriodMembers, currentPeriodMembers)

        // Seed additional committee members from randomly generated members, max 6 per committee
        seedCommitteeMembers(allCommittees, memberPool)

        // Consume recovery tokens for enabled users (mirrors RecoveryController flows)
        createdUsers.values.forEach(Consumer { u: User? ->
            if (u!!.enabled && u.getRecoveryTokens() != null) {
                u.getRecoveryTokens().forEach(Consumer { rt: RecoveryToken? -> rt!!.setConsumedAt(Instant.now()) })
            }
        })

        // ---- Events ----
        val eventsConfig = List.of<MutableMap<String?, Any?>?>(
            event("Past Approved Members-Only with Sign-Up", true, true, true, -30, -29, true),
            event("Past Approved Members-Only without Sign-Up", true, true, false, -25, -24, false),
            event("Past Approved Public with Sign-Up", true, false, true, -20, -19, true),
            event("Past Approved Public without Sign-Up", true, false, false, -15, -14, false),
            event("Past Not-Approved Members-Only with Sign-Up", false, true, true, -10, -9, true),
            event("Past Not-Approved Members-Only without Sign-Up", false, true, false, -8, -7, false),
            event("Past Not-Approved Public with Sign-Up", false, false, true, -6, -5, true),
            event("Past Not-Approved Public without Sign-Up", false, false, false, -4, -3, false),
            event("Future Approved Members-Only with Sign-Up", true, true, true, 5, 6, true),
            event("Future Approved Members-Only without Sign-Up", true, true, false, 7, 8, false),
            event("Future Approved Public with Sign-Up", true, false, true, 10, 11, true),
            event("Future Approved Public without Sign-Up", true, false, false, 12, 13, false),
            event("Future Not-Approved Members-Only with Sign-Up", false, true, true, 15, 16, true),
            event("Future Not-Approved Members-Only without Sign-Up", false, true, false, 17, 18, false),
            event("Future Not-Approved Public with Sign-Up", false, false, true, 20, 21, true),
            event("Future Not-Approved Public without Sign-Up", false, false, false, 22, 23, false),
            event("Event with Sign-Up Form", true, false, true, 25, 26, true)
        )

        val events = ArrayList<Event>()
        var committeeIndex = 0
        for (ec in eventsConfig) {
            // Distribute events across committees so they all host events
            val hostingCommittee: Committee?
            if (committeeIndex < allCommittees.size) {
                hostingCommittee = allCommittees.get(committeeIndex)
            } else {
                hostingCommittee = allCommittees.get(rnd.nextInt(allCommittees.size))
            }
            committeeIndex++

            val e = createEvent(
                hostingCommittee,
                ec.get("title") as String?,
                (ec.get("approved") as kotlin.Boolean?)!!,
                (ec.get("membersOnly") as kotlin.Boolean?)!!,
                (ec.get("signUp") as kotlin.Boolean?)!!,
                (ec.get("startOffset") as Int?)!!,
                (ec.get("endOffset") as Int?)!!,
                (ec.get("withForm") as kotlin.Boolean?)!!
            )
            events.add(e)
        }

        // Create a "previously approved" event, then revoke it after collecting sign-ups (mirrors approve endpoint)
        var previouslyApproved = createEvent(
            testCommittee,
            "Previously Approved Event",
            true,  // start as approved
            false,
            true,
            30,
            31,
            true
        )
        events.add(previouslyApproved)

        // Seed initial "reference" signups like controllers would set principal → userId
        for (e in events) {
            if (e.signUp) {
                createEventSignUpWithAnswers(memberUser, e)
                if (!e.membersOnly) {
                    createEventSignUpWithAnswers(normalUser, e)
                }
            }
        }

        // Add lots of realistic signups, avoiding duplicates and respecting members-only
        for (e in events) {
            if (e.signUp) seedSignUps(e, memberPool, guestPool)
        }

        // Add guest sign-ups for public events with sign-up enabled
        for (e in events) {
            if (e.signUp && !e.membersOnly) {
                seedGuestSignUps(e)
            }
        }

        // Add a couple of explicit sign-ups to the "previously approved" event
        createEventSignUpWithAnswers(boardUser, previouslyApproved)
        createEventSignUpWithAnswers(committeeUser, previouslyApproved)

        // Revoke approval on that event (mirrors EventController.approve)
        previouslyApproved = eventService.findById(previouslyApproved.getId())
        previouslyApproved.setApproved(false)
        eventService.update(previouslyApproved)

        // Make some random users inactive (not one of the original few reference users)
        deactivateRandomUsers(
            10,
            createdUsers.values,
            Set.of<String?>(
                boardUser.username,
                committeeUser.username,
                memberUser.username,
                normalUser.username,
                guestInactive.username
            )
        )

        DatabaseSeeder.log.info("Database seeding completed.")
        DatabaseSeeder.log.info("Created users ({}): {}", createdUsers.size, String.join(", ", createdUsers.keys))
        DatabaseSeeder.log.info("Created {} events", events.size)
    }

    // ---- Helpers below ------------------------------------------------------
    private fun createRandomUser(baseRole: Role, includeAddress: Boolean): User {
        val first = safeName(faker.name().firstName())
        val last = safeName(faker.name().lastName())
        val base = (first + "." + last).lowercase(Locale.getDefault())
        val username = uniqueUsername(base)
        val email = username + "@esa-blueshell.nl"

        var u = createUserWithRole(username, first, last, email, baseRole, includeAddress)

        // Mirror AddressController semantics if we want to "add address after create"
        if (!includeAddress && rnd.nextBoolean()) {
            val addr = createAddressEntity()
            u.setAddress(addr)
            u = userService.update(u)
        }

        return u
    }

    private fun safeName(s: kotlin.String): kotlin.String {
        return s.replace("[^A-Za-z0-9]".toRegex(), "")
    }

    private fun uniqueUsername(base: kotlin.String?): kotlin.String? {
        var u = base
        var i = 1
        while (userService.existsByUsername(u)) {
            u = base + "." + i++
        }
        return u
    }

    private fun buildAddress(): Address {
        val address = Address()
        address.setStreet(faker.address().streetName())
        address.setHouseNumber(faker.number().numberBetween(1, 200).toString())
        address.setCity(faker.address().city())
        address.setZipCode(faker.address().zipCode())
        address.setCountry(faker.address().country())
        return address
    }

    private fun createMembership(period: ContributionPeriod, user: User): Membership? {
        val membership = Membership()
        membership.setStartDate(period.getStartDate())
        membership.setEndDate(period.getEndDate())
        membership.setMemberType(MemberType.REGULAR)
        membership.setUserId(user.getId())
        return membershipService.create(membership)
    }

    private fun event(
        title: kotlin.String?,
        approved: Boolean,
        membersOnly: Boolean,
        signUp: Boolean,
        startOffset: Int,
        endOffset: Int,
        withForm: Boolean
    ): MutableMap<kotlin.String?, Any?> {
        val m = LinkedHashMap<kotlin.String?, Any?>()
        m.put("title", title)
        m.put("approved", approved)
        m.put("membersOnly", membersOnly)
        m.put("signUp", signUp)
        m.put("startOffset", startOffset)
        m.put("endOffset", endOffset)
        m.put("withForm", withForm)
        return m
    }

    private fun createUserWithRole(
        username: kotlin.String?,
        firstName: kotlin.String?,
        lastName: kotlin.String?,
        email: kotlin.String,
        role: Role,
        includeAddress: Boolean
    ): User {
        val user = User()
        user.setUsername(username)
        user.setPassword(passwordEncoder.encode("password"))
        user.setFirstName(firstName)
        user.setLastName(lastName)
        user.setEmail(email)
        user.setEnabled(true)
        user.setRoles(role.getAllInheritedRoles())
        user.setNewsletter(false)
        user.setDateOfBirth(Date.valueOf(LocalDate.of(1995, 1, 1)))
        user.setConsentPrivacy(true)
        user.setConsentGdpr(true)
        if (includeAddress) user.setAddress(createAddressEntity())
        return userService.create(user)
    }

    private fun createInactiveUser(
        username: kotlin.String?,
        firstName: kotlin.String?,
        lastName: kotlin.String?,
        email: kotlin.String,
        includeAddress: Boolean
    ): User {
        val user = User()
        user.setUsername(username)
        user.setPassword(passwordEncoder.encode("temporary"))
        user.setFirstName(firstName)
        user.setLastName(lastName)
        user.setEmail(email)
        user.setEnabled(false)
        user.setRoles(Set.of<Role?>(Role.GUEST))
        user.setNewsletter(false)
        user.setDateOfBirth(Date.valueOf(LocalDate.of(1995, 1, 1)))
        user.setConsentPrivacy(true)
        user.setConsentGdpr(true)
        if (includeAddress) user.setAddress(createAddressEntity())
        return userService.create(user)
    }

    private fun createAddressEntity(): Address {
        val address = Address()
        address.setCountry("Netherlands")
        address.setCity("Amsterdam")
        address.setStreet("Test Street")
        address.setHouseNumber("123")
        address.setZipCode("1234 AB")
        return address
    }

    private fun createCommittee(name: kotlin.String?, description: kotlin.String?): Committee {
        val committee = Committee()
        committee.setName(name)
        committee.setDescription(description)
        committee.setMembers(HashSet<CommitteeMember?>())
        return committeeService.create(committee)
    }

    private fun createCommitteeMember(user: User?, committee: Committee?, role: kotlin.String?): CommitteeMember? {
        if (committee == null || user == null) return null

        val key = committee.getId().toString() + ":" + user.getId()
        if (committeeMemberships.contains(key)) {
            // Already in this committee; avoid duplicates
            return null
        }

        val currentCount = committeeMemberCounts.getOrDefault(committee.getId(), 0)!!
        if (currentCount >= 6) {
            DatabaseSeeder.log.debug(
                "Committee '{}' already has max members (6), skipping {}",
                committee.getName(),
                user.username
            )
            return null
        }

        val member = CommitteeMember()
        member.setUserId(user.getId())
        member.setCommittee(committee)
        member.setRole(role)

        val created = committeeMemberService.create(member)
        committeeMemberCounts.put(committee.getId(), currentCount + 1)
        committeeMemberships.add(key)
        return created
    }

    private fun createContributionPeriod(start: LocalDate?, end: LocalDate?): ContributionPeriod {
        val period = ContributionPeriod()
        period.setStartDate(start)
        period.setEndDate(end)
        period.setHalfYearFee(10.0)
        period.setFullYearFee(18.0)
        period.setAlumniFee(5.0)
        return contributionPeriodService.create(period)
    }

    private fun createContribution(user: User, period: ContributionPeriod): Contribution? {
        val c = Contribution()
        c.setUserId(user.getId())
        c.setContributionPeriodId(period.getId())
        val created = contributionService.create(c)

        contributionsByPeriod
            .computeIfAbsent(period.getId()) { id: Long? -> HashSet<Long?>() }
            .add(user.getId())

        return created
    }

    private fun createEvent(
        committee: Committee?,
        title: kotlin.String?,
        approved: Boolean,
        membersOnly: Boolean,
        signUp: Boolean,
        startDaysOffset: Int,
        endDaysOffset: Int,
        withForm: Boolean
    ): Event {
        var survey: Survey? = null
        if (signUp && withForm) {
            survey = createSurveyWithAllQuestionTypes()
            // If cascade PERSIST is not configured on Event.signUpForm, uncomment:
            // survey = surveyService.create(survey);
        }
        val event = Event()
        event.setCommitteeId(if (committee != null) committee.getId() else null)
        event.setTitle(title)
        event.setDescription("Description for " + title)
        event.setLocation("Test Location")
        event.setStartTime(Instant.now().plus(startDaysOffset.toLong(), ChronoUnit.DAYS))
        event.setEndTime(Instant.now().plus(endDaysOffset.toLong(), ChronoUnit.DAYS))
        event.setApproved(approved)
        event.setMembersOnly(membersOnly)
        event.setSignUp(signUp)
        event.setMemberPrice(10.0)
        event.setPublicPrice(15.0)
        event.setSignUpForm(survey)
        return eventService.create(event)
    }

    private fun findExistingSignUp(eventId: Long?, userId: Long?): Optional<EventSignUp?> {
        return eventSignUpService.findByEventId(eventId).stream()
            .filter { su: EventSignUp? -> su!!.getUserId() == userId }
            .findFirst()
    }

    private fun createEventSignUpWithAnswers(user: User, event: Event): EventSignUp? {
        // 1) Idempotency guard: user can sign up to an event only once
        val existing = findExistingSignUp(event.getId(), user.getId())
        if (existing.isPresent) {
            return existing.get()
        }

        val signUp = EventSignUp()
        signUp.setEventId(event.getId())
        signUp.setUserId(user.getId())

        val answers = LinkedHashSet<Answer?>()
        val form = event.getSignUpForm()
        if (event.signUp && form != null && form.getQuestions() != null) {
            for (q in form.getQuestions()) {
                answers.add(createAnswerForQuestion(q))
            }
        }
        signUp.setAnswers(answers)

        try {
            // 2) Normal path
            return eventSignUpService.create(signUp)
        } catch (ex: DataIntegrityViolationException) {
            // 3) Defensive: if another thread/seed pass created it just now, fetch and return it
            DatabaseSeeder.log.warn(
                "Sign-up already exists for eventId={}, userId={}, skipping duplicate insert.",
                event.getId(), user.getId()
            )
            return findExistingSignUp(event.getId(), user.getId())
                .orElseThrow<DataIntegrityViolationException?>(Supplier { ex }) // rethrow only if truly not present
        }
    }

    private fun seedSignUps(event: Event, memberPool: MutableList<User?>, guestPool: MutableList<User?>) {
        // Build candidate list
        val candidates: MutableList<User> = ArrayList<User>(memberPool)
        if (!event.membersOnly) candidates.addAll(guestPool)

        // Avoid duplicates: find existing signups for this event
        val existingUserIds = eventSignUpService.findByEventId(event.getId()).stream()
            .map<Long?> { obj: EventSignUp? -> obj!!.getUserId() }
            .filter { obj: Long? -> Objects.nonNull(obj) }
            .collect(Collectors.toSet())

        // Shuffle and pick a realistic count
        Collections.shuffle(candidates, rnd)
        val maxDesired = min(candidates.size, rnd.nextInt(20, 45))
        var added = 0

        for (u in candidates) {
            if (added >= maxDesired) break
            if (existingUserIds.contains(u.getId())) continue
            createEventSignUpWithAnswers(u, event)
            existingUserIds.add(u.getId())
            added++
        }
    }

    private fun createSurveyWithAllQuestionTypes(): Survey {
        val survey = Survey()
        val questions = LinkedHashSet<Question>()
        questions.add(buildQuestion(1L, QuestionType.DESCRIPTION, "Important information:", null))
        questions.add(
            buildQuestion(
                2L,
                QuestionType.RADIO,
                "Please select one option:",
                ArrayList<kotlin.String?>(DEFAULT_RADIO_CHOICES)
            )
        )
        questions.add(
            buildQuestion(
                3L,
                QuestionType.CHECKBOX,
                "Select all that apply:",
                ArrayList<kotlin.String?>(DEFAULT_CHECKBOX_CHOICES)
            )
        )
        questions.add(buildQuestion(4L, QuestionType.OPEN, "What are your thoughts?", null))
        survey.setQuestions(questions)
        for (q in survey.getQuestions()) q.setSurvey(survey)
        return survey
    }

    private fun buildQuestion(
        idx: Long?,
        type: QuestionType?,
        label: kotlin.String?,
        choiceLabels: MutableList<kotlin.String?>?
    ): Question {
        val q = Question()
        q.setIdx(idx)
        q.setType(type)
        q.setLabel(label)
        q.setChoiceLabels(choiceLabels)
        return q
    }

    private fun createAnswerForQuestion(q: Question): Answer {
        val a = Answer()
        a.setQuestion(q)
        a.setQuestionId(q.getId())
        when (q.getType()) {
            QuestionType.OPEN -> a.setTextResponse("Sample answer text")
            QuestionType.RADIO -> {
                val n = sizeOfChoices(q)
                val selections = ArrayList<Boolean?>(Collections.nCopies<Boolean?>(max(n, 1), Boolean.FALSE))
                if (n > 0) selections.set(0, Boolean.TRUE)
                a.setOptionSelections(selections)
            }

            QuestionType.CHECKBOX -> {
                val n = sizeOfChoices(q)
                val selections =
                    ArrayList<kotlin.Boolean?>(Collections.nCopies<kotlin.Boolean?>(max(n, 1), Boolean.FALSE))
                if (n > 0) selections.set(0, Boolean.TRUE)
                if (n > 2) selections.set(2, Boolean.TRUE)
                a.setOptionSelections(selections)
            }

            QuestionType.DESCRIPTION -> {
                // no-op
            }
        }
        return a
    }

    private fun sizeOfChoices(q: Question): Int {
        return if (q.getChoiceLabels() == null) 0 else q.getChoiceLabels().size
    }

    private fun buildRandomGuest(): Guest {
        val guest = Guest()
        guest.setName(faker.name().fullName())
        guest.setDiscord(faker.name().username() + "#" + faker.number().numberBetween(1000, 9999))
        guest.setEmail(faker.internet().emailAddress())
        guest.setPhoneNumber(faker.phoneNumber().phoneNumber())
        guest.setAccessToken(UUID.randomUUID().toString())
        return guest
    }

    private fun createGuestEventSignUpWithAnswers(guest: Guest?, event: Event): EventSignUp? {
        val signUp = EventSignUp()
        signUp.setEventId(event.getId())
        signUp.setUserId(null) // guest signup, not a registered user
        signUp.setGuest(guest)

        val answers = LinkedHashSet<Answer?>()
        val form = event.getSignUpForm()
        if (event.signUp && form != null && form.getQuestions() != null) {
            for (q in form.getQuestions()) {
                answers.add(createAnswerForQuestion(q))
            }
        }
        signUp.setAnswers(answers)

        return eventSignUpService.create(signUp)
    }

    private fun seedGuestSignUps(event: Event) {
        // Only public events with sign-up should get guest sign-ups
        if (event.membersOnly || !event.signUp) {
            return
        }

        // Between 5 and 20 guest signups per public sign-up event
        val guestCount = rnd.nextInt(5, 21)

        for (i in 0..<guestCount) {
            val guest = buildRandomGuest()
            createGuestEventSignUpWithAnswers(guest, event)
        }
    }

    // ---- New helper logic for contribution ratios, committees & inactive users ----
    private fun ensureContributionRatios(
        previousPeriod: ContributionPeriod,
        currentPeriod: ContributionPeriod,
        previousMembers: MutableList<User?>,
        currentMembers: MutableList<User?>
    ) {
        enforceContributionRatioForPeriod(previousPeriod, previousMembers, 0.75) // 75% past members
        enforceContributionRatioForPeriod(currentPeriod, currentMembers, 0.60) // 60% current members
    }

    private fun enforceContributionRatioForPeriod(
        period: ContributionPeriod,
        members: MutableList<User?>,
        ratio: Double
    ) {
        if (members.isEmpty()) return

        // Deduplicate by user id
        val distinctMembers: MutableList<User?> = members.stream()
            .filter { obj: User? -> Objects.nonNull(obj) }
            .collect(
                Collectors.collectingAndThen(
                    Collectors.toMap(
                        Function { obj: User? -> obj!!.getId() },
                        Function { u: User? -> u },
                        BinaryOperator { u1: User?, u2: User? -> u1 }),
                    Function { m: MutableMap<Long?, User?>? -> ArrayList<User?>(m!!.values) }
                ))

        val total = distinctMembers.size
        if (total == 0) return

        val desiredPaid = Math.round(total * ratio).toInt()
        val paidUserIds = contributionsByPeriod
            .getOrDefault(period.getId(), mutableSetOf<Long?>())

        val currentPaid = paidUserIds.size
        if (currentPaid >= desiredPaid) {
            // Already at or above the desired ratio
            return
        }

        val unpaid: MutableList<User> = distinctMembers.stream()
            .filter { u: User? -> !paidUserIds.contains(u!!.getId()) }
            .collect(Collectors.toList())

        Collections.shuffle(unpaid, rnd)
        val needed = min(desiredPaid - currentPaid, unpaid.size)
        for (i in 0..<needed) {
            createContribution(unpaid.get(i), period)
        }
    }

    private fun seedCommitteeMembers(committees: MutableList<Committee>?, memberPool: MutableList<User?>) {
        if (committees == null || committees.isEmpty()) return

        // Use randomly generated members, but avoid spamming the original reference users
        val coreUsernames = mutableSetOf<kotlin.String?>(
            "board.user",
            "committee.user",
            "member.user",
            "normal.user",
            "guest.inactive"
        )

        val candidates: MutableList<User> = memberPool.stream()
            .filter { u: User? -> u != null && !coreUsernames.contains(u.username) }
            .collect(
                Collectors.collectingAndThen(
                    Collectors.toMap(
                        Function { obj: User? -> obj!!.getId() },
                        Function { u: User? -> u },
                        BinaryOperator { u1: User?, u2: User? -> u1 }),
                    Function { m: MutableMap<Long?, User?>? -> ArrayList<User?>(m!!.values) }
                ))

        Collections.shuffle(candidates, rnd)
        val iterator: MutableIterator<User?> = candidates.iterator()

        for (committee in committees) {
            val currentCount = committeeMemberCounts.getOrDefault(committee.getId(), 0)!!
            val remainingSlots = 6 - currentCount
            if (remainingSlots <= 0) continue

            // Aim for between 3 and 6 members total per committee
            val desiredTotal = rnd.nextInt(3, 7)
            var targetAdditionalMembers = max(0, desiredTotal - currentCount)
            targetAdditionalMembers = min(targetAdditionalMembers, remainingSlots)

            var i = 0
            while (i < targetAdditionalMembers && iterator.hasNext()) {
                val candidate = iterator.next()
                val role = pickRoleForPosition(currentCount + i)
                createCommitteeMember(candidate, committee, role)
                i++
            }
        }
    }

    private fun pickRoleForPosition(position: Int): kotlin.String {
        // Some sensible role distribution: Chair, Secretary, Treasurer, Event Manager, PR, Member
        return when (position) {
            0 -> "Chair"
            1 -> "Secretary"
            2 -> "Treasurer"
            3 -> "Event Manager"
            4 -> "PR Officer"
            else -> "Member"
        }
    }

    private fun deactivateRandomUsers(
        count: Int,
        allUsers: MutableCollection<User?>,
        excludedUsernames: MutableSet<kotlin.String?>
    ) {
        val candidates: MutableList<User> = allUsers.stream()
            .filter { obj: User? -> Objects.nonNull(obj) }
            .filter { obj: User? -> obj!!.enabled }
            .filter { u: User? -> !excludedUsernames.contains(u!!.username) }
            .collect(Collectors.toList())

        if (candidates.isEmpty()) return

        Collections.shuffle(candidates, rnd)
        val limit = min(count, candidates.size)

        for (i in 0..<limit) {
            val u = userService.findById(candidates.get(i).getId())

            u.setEnabled(false)
            val updated = userService.update(u)
            createdUsers.put(updated.getUsername(), updated)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DatabaseSeeder::class.java)
        private val DEFAULT_RADIO_CHOICES =
            mutableListOf<kotlin.String?>("Option A", "Option B", "Option C", "Option D")
        private val DEFAULT_CHECKBOX_CHOICES = mutableListOf<kotlin.String?>(
            "Choice 1", "Choice 2", "Choice 3", "Choice 4", "Choice 5"
        )
    }
}
