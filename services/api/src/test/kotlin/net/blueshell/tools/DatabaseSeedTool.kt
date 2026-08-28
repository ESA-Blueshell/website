package net.blueshell.tools

import com.github.javafaker.Faker
import net.blueshell.api.ApiApplication
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.factory.board.persistence.BoardFactory
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.file.persistence.FileFactory
import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.security.crypto.password.PasswordEncoder
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private val log = LoggerFactory.getLogger("DatabaseSeedTool")
private const val DEFAULT_CONFIG_RESOURCE = "database-seeder.yml"
private val SEEDER_SYSTEM_PROPERTIES = mapOf(
    "server.port" to "0",
    "management.server.port" to "0",
)
private const val DEFAULT_ACTIVE_PROFILE = "default"

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (parsed.showHelp) {
        printUsage()
        return
    }

    applySeederSystemProperties(parsed.profile)

    val loadedConfig = loadConfig(parsed.configPath).normalized()

    val context = SpringApplicationBuilder(ApiApplication::class.java)
        .properties(
            "spring.main.banner-mode=off",
            "spring.main.log-startup-info=false",
        )
        .run()

    context.use {
        val seeder = DatabaseSeedRunner(
            userFactory = it.getBean<UserFactory>(),
            committeeFactory = it.getBean<CommitteeFactory>(),
            contributionFactory = it.getBean<ContributionFactory>(),
            boardFactory = it.getBean<BoardFactory>(),
            eventFactory = it.getBean<EventFactory>(),
            fileFactory = it.getBean<FileFactory>(),
            persistence = it.getBean<FactoryPersistenceSupport>(),
            passwordEncoder = it.getBean<PasswordEncoder>(),
        )
        val summary = seeder.seed(loadedConfig)
        log.info("Database seeding completed: {}", summary)
    }
}

private fun applySeederSystemProperties(requestedProfile: String?) {
    // Prevent the seeding process from trying to bind the API HTTP port.
    SEEDER_SYSTEM_PROPERTIES.forEach { (key, value) ->
        if (System.getProperty(key).isNullOrBlank()) {
            System.setProperty(key, value)
        }
    }

    val profile = requestedProfile?.trim().takeUnless { it.isNullOrEmpty() }
        ?: System.getProperty("spring.profiles.active")?.trim().takeUnless { it.isNullOrEmpty() }
        ?: System.getenv("SPRING_PROFILES_ACTIVE")?.trim().takeUnless { it.isNullOrEmpty() }
        ?: DEFAULT_ACTIVE_PROFILE
    System.setProperty("spring.profiles.active", profile)
}

private class DatabaseSeedRunner(
    private val userFactory: UserFactory,
    private val committeeFactory: CommitteeFactory,
    private val contributionFactory: ContributionFactory,
    private val boardFactory: BoardFactory,
    private val eventFactory: EventFactory,
    private val fileFactory: FileFactory,
    private val persistence: FactoryPersistenceSupport,
    private val passwordEncoder: PasswordEncoder,
) {
    private var sequence = 0L
    private val faker = Faker(Locale.ENGLISH)
    private val runToken = faker.bothify("??##").lowercase()
    private val unsafeTokenRegex = Regex("[^a-z0-9]+")

    fun seed(config: SeederConfig): SeedSummary {
        val random = Random(config.randomSeed)
        val committees = createCommittees(config.organization.committees)

        val members = createUsers(
            role = Role.MEMBER,
            count = config.users.members,
            usernamePrefix = "member",
            includeMemberData = true,
            defaultPassword = config.users.defaultPassword,
        )
        val committeeMembers = createUsers(
            role = Role.COMMITTEE,
            count = config.users.committeeMembers,
            usernamePrefix = "committee",
            includeMemberData = true,
            defaultPassword = config.users.defaultPassword,
        )
        val boardMembers = createUsers(
            role = Role.BOARD,
            count = config.users.boardMembers,
            usernamePrefix = "board",
            includeMemberData = true,
            defaultPassword = config.users.defaultPassword,
        )
        val admins = createUsers(
            role = Role.ADMIN,
            count = max(1, config.users.admins),
            usernamePrefix = "admin",
            includeMemberData = true,
            defaultPassword = config.users.defaultPassword,
        )
        val guests = createUsers(
            role = Role.GUEST,
            count = config.users.guestUsers,
            usernamePrefix = "guest",
            includeMemberData = false,
            defaultPassword = config.users.defaultPassword,
        )
        val configuredUsers = createConfiguredUsers(config.users)
        val allUsers = (members + committeeMembers + guests + boardMembers + admins + configuredUsers).distinctBy { it.id }
        val nonGuestUsers = allUsers.filterNot { it.hasRole(Role.GUEST) }

        seedCommitteeMemberships(committees, committeeMembers)
        seedBoard(config, boardMembers, admins)
        val eventSummary = seedEvents(config.events, committees, nonGuestUsers, random)
        val contributionSummary = seedMembershipAndContributionData(
            config = config.contributions,
            membershipCandidates = nonGuestUsers,
            random = random,
        )

        return SeedSummary(
            members = members.size,
            committeeMembers = committeeMembers.size,
            guestUsers = guests.size,
            boardMembers = boardMembers.size,
            admins = admins.size,
            configuredUsers = configuredUsers.size,
            committees = committees.size,
            events = eventSummary.seededEvents,
            eventsWithSignUpForms = eventSummary.withSignUpForms,
            eventsWithBanners = eventSummary.withBanners,
            eventSignUps = eventSummary.signUps,
            contributionPeriods = contributionSummary.periods,
            activeMemberships = contributionSummary.activeMemberships,
            pastMemberships = contributionSummary.pastMemberships,
            neverMembers = contributionSummary.neverMembers,
            paidPreviousYear = contributionSummary.paidPreviousYear,
            paidCurrentYear = contributionSummary.paidCurrentYear,
            neverPaidMembers = contributionSummary.neverPaidMembers,
        )
    }

    private fun createCommittees(count: Int): List<Committee> {
        return (1..count).map {
            val topic = shortNameWord()
            val color = shortNameWord()
            committeeFactory.create(
                name = "$topic $color".take(28),
                description = faker.company().catchPhrase().take(96),
            )
        }
    }

    private fun createUsers(
        role: Role,
        count: Int,
        usernamePrefix: String,
        includeMemberData: Boolean,
        defaultPassword: String,
    ): List<User> {
        return (1..count).map {
            val suffix = nextSuffix()
            val firstName = faker.name().firstName().trim().ifBlank { usernamePrefix.replaceFirstChar { it.uppercase() } }
            val lastName = faker.name().lastName().trim().ifBlank { role.name.lowercase().replaceFirstChar { it.uppercase() } }
            val baseToken = sanitizeToken(faker.name().username()).take(16).ifBlank {
                sanitizeToken("${firstName}_${lastName}")
            }
            val user = userFactory.buildUserWithRole(role, enabled = true).apply {
                username = "${usernamePrefix}_${baseToken}_$suffix"
                email = "${baseToken}_$suffix@example.test"
                discord = "${baseToken.take(10)}#${faker.number().digits(4)}"
                this.firstName = firstName
                this.lastName = lastName
                phoneNumber = "06${sequence.toString().padStart(8, '0').takeLast(8)}"
                password = requireNotNull(passwordEncoder.encode(defaultPassword)) { "PasswordEncoder returned null hash" }
            }

            val persisted = persistence.persist(user)
            if (includeMemberData) {
                enrichMemberData(persisted, suffix)
            }
            persisted
        }
    }

    private fun createConfiguredUsers(config: UserSeedConfig): List<User> {
        return config.namedUsers.map { named ->
            val suffix = nextSuffix()
            val user = userFactory.buildUserWithRole(named.role, enabled = true).apply {
                username = named.username
                email = "${sanitizeToken(named.username)}@example.test"
                discord = "${sanitizeToken(named.username).take(10)}#${faker.number().digits(4)}"
                firstName = named.firstName ?: named.username
                lastName = named.lastName ?: named.role.name.lowercase().replaceFirstChar { it.uppercase() }
                phoneNumber = "06${suffix.takeLast(8).padStart(8, '0')}"
                password = requireNotNull(passwordEncoder.encode(config.defaultPassword)) { "PasswordEncoder returned null hash" }
            }

            val persisted = persistence.persist(user)
            val includeMemberData = named.includeMemberData ?: named.role.matchesRole(Role.MEMBER)
            val includeAddress = named.includeAddress ?: includeMemberData
            if (includeMemberData || includeAddress) {
                enrichUserData(
                    user = persisted,
                    suffix = suffix,
                    includeAddress = includeAddress,
                    includeMemberData = includeMemberData,
                )
            }
            persisted
        }
    }

    private fun enrichMemberData(user: User, suffix: String) {
        enrichUserData(
            user = user,
            suffix = suffix,
            includeAddress = true,
            includeMemberData = true,
        )
    }

    private fun enrichUserData(
        user: User,
        suffix: String,
        includeAddress: Boolean,
        includeMemberData: Boolean,
    ) {
        val zipPrefix = suffix.takeLast(4).padStart(4, '0')
        if (includeAddress) {
            val city = faker.address().cityName().trim().ifBlank { "Enschede" }
            val street = faker.address().streetName().trim().ifBlank { "Seed Street" }
            val houseNumber = faker.address().buildingNumber()
                .trim()
                .takeIf { it.isNotBlank() && it.any(Char::isDigit) }
                ?: suffix.takeLast(3).padStart(3, '0')
            val address = userFactory.buildAddress(
                user = user,
                city = city,
                street = street,
                houseNumber = houseNumber,
                zipCode = "${zipPrefix}AB",
            )
            user.replaceAddress(address)
        }
        if (includeMemberData) {
            val memberProfile = userFactory.buildMemberProfile(user).apply {
                studentNumber = "s$suffix"
            }
            user.replaceMemberProfile(memberProfile)
        }

        persistence.persist(user)
    }

    private fun seedCommitteeMemberships(
        committees: List<Committee>,
        committeeMembers: List<User>,
    ) {
        committeeMembers.forEachIndexed { index, user ->
            val committee = committees[index % committees.size]
            val role = if (index % 4 == 0) "Chair" else "Member"
            committeeFactory.createMember(committee, user, role)
        }
    }

    private fun seedBoard(
        config: SeederConfig,
        boardMembers: List<User>,
        admins: List<User>,
    ) {
        val board = boardFactory.create(
            name = config.organization.boardName,
            candidate = config.organization.boardCandidate,
            startDate = LocalDate.now().minusMonths(2),
        )

        val roles = listOf("CHAIR", "TREASURER", "SECRETARY", "GENERAL")
        val usersForBoard = (boardMembers + admins.take(1)).distinctBy { it.id }
        usersForBoard.forEachIndexed { index, user ->
            boardFactory.createMember(
                board = board,
                user = user,
                role = roles[index % roles.size],
                startDate = LocalDate.now().minusMonths((index + 1).toLong()),
            )
        }
    }

    private fun seedEvents(
        config: EventSeedConfig,
        committees: List<Committee>,
        seedUsers: List<User>,
        random: Random,
    ): EventSeedResult {
        val now = Instant.now()
        val eventScenarios = listOf(
            EventScenario(
                "Past Approved Public",
                config.pastApprovedPublic,
                approved = true,
                membersOnly = false,
                past = true
            ),
            EventScenario(
                "Past Approved MembersOnly",
                config.pastApprovedMembersOnly,
                approved = true,
                membersOnly = true,
                past = true
            ),
            EventScenario(
                "Past Unapproved Public",
                config.pastUnapprovedPublic,
                approved = false,
                membersOnly = false,
                past = true
            ),
            EventScenario(
                "Past Unapproved MembersOnly",
                config.pastUnapprovedMembersOnly,
                approved = false,
                membersOnly = true,
                past = true
            ),
            EventScenario(
                "Future Approved Public",
                config.futureApprovedPublic,
                approved = true,
                membersOnly = false,
                past = false
            ),
            EventScenario(
                "Future Approved MembersOnly",
                config.futureApprovedMembersOnly,
                approved = true,
                membersOnly = true,
                past = false
            ),
            EventScenario(
                "Future Unapproved Public",
                config.futureUnapprovedPublic,
                approved = false,
                membersOnly = false,
                past = false
            ),
            EventScenario(
                "Future Unapproved MembersOnly",
                config.futureUnapprovedMembersOnly,
                approved = false,
                membersOnly = true,
                past = false
            ),
        )

        val persistedEvents = mutableListOf<net.blueshell.api.event.persistence.Event>()
        eventScenarios.forEach { scenario ->
            repeat(scenario.count) { index ->
                val committee = committees[random.nextInt(committees.size)]
                val signUp = index % 2 == 0
                val dayOffset = (index + 1).toLong()
                val start = if (scenario.past) {
                    now.minus(dayOffset, ChronoUnit.DAYS)
                } else {
                    now.plus(dayOffset, ChronoUnit.DAYS)
                }

                val event = eventFactory.build(
                    committee = committee,
                    approved = scenario.approved,
                    membersOnly = scenario.membersOnly,
                    signUp = signUp,
                    title = buildEventTitle(),
                ).apply {
                    startTime = start
                    endTime = start.plus(config.durationHours.toLong(), ChronoUnit.HOURS)
                    memberPrice = 5.0 + (index % 3)
                    publicPrice = if (membersOnly) null else 10.0 + (index % 4)
                    description = faker.lorem().sentence(8).take(120)
                    location = faker.address().cityName().ifBlank { "Campus" }
                }
                persistedEvents += persistence.persist(event)
            }
        }

        val signUpFormCandidates = persistedEvents.filter { it.signUp }.shuffled(random)
        val signUpFormTarget = targetCount(signUpFormCandidates.size, config.signUpFormRatio)
        signUpFormCandidates.take(signUpFormTarget).forEach { event ->
            event.replaceSignUpForm(buildEventSignUpForm())
            persistence.persist(event)
        }

        val bannerCandidates = persistedEvents.shuffled(random)
        val bannerTarget = if (seedUsers.isNotEmpty()) {
            targetCount(bannerCandidates.size, config.bannerRatio)
        } else {
            0
        }
        bannerCandidates.take(bannerTarget).forEach { event ->
            val uploader = seedUsers[random.nextInt(seedUsers.size)]
            val file = fileFactory.create(
                uploader = uploader,
                name = "seed-banner-${nextSuffix()}.png",
            )
            eventFactory.createBanner(event, file)
        }

        var seededSignUps = 0
        val signUpUserCandidates = seedUsers.distinctBy { it.id }
        persistedEvents
            .filter { it.signUp }
            .forEach { event ->
                val targetSignUps = targetCount(signUpUserCandidates.size, config.signUpRatio)
                signUpUserCandidates
                    .shuffled(random)
                    .take(targetSignUps)
                    .forEach { user ->
                        eventFactory.createSignUp(event = event, user = user)
                        seededSignUps++
                    }
            }

        return EventSeedResult(
            seededEvents = persistedEvents.size,
            withSignUpForms = signUpFormTarget,
            withBanners = bannerTarget,
            signUps = seededSignUps,
        )
    }

    private fun buildEventSignUpForm(): Survey {
        val survey = Survey()
        survey.addQuestion(
            Question(
                idx = 0L,
                survey = survey,
                type = QuestionType.DESCRIPTION,
                label = "Info",
                choiceLabels = mutableListOf(),
            )
        )
        survey.addQuestion(
            Question(
                idx = 1L,
                survey = survey,
                type = QuestionType.RADIO,
                label = "Meal",
                choiceLabels = mutableListOf("Standard", "Vegetarian", "Vegan"),
            )
        )
        survey.addQuestion(
            Question(
                idx = 2L,
                survey = survey,
                type = QuestionType.CHECKBOX,
                label = "Preferences",
                choiceLabels = mutableListOf("No alcohol", "Lactose free", "Accessibility"),
            )
        )
        survey.addQuestion(
            Question(
                idx = 3L,
                survey = survey,
                type = QuestionType.OPEN,
                label = "Notes",
            )
        )
        return survey
    }

    private fun seedMembershipAndContributionData(
        config: ContributionSeedConfig,
        membershipCandidates: List<User>,
        random: Random,
    ): ContributionSeedResult {
        val currentYear = LocalDate.now().year
        val periodShift = faker.number().numberBetween(0, 17)
        val previousPeriod = createContributionPeriodWithRetry(
            baseStart = LocalDate.of(currentYear - 1, 1, 1).plusDays(periodShift.toLong()),
            baseEnd = LocalDate.of(currentYear - 1, 12, 31).minusDays(periodShift.toLong()),
        )
        val currentPeriod = createContributionPeriodWithRetry(
            baseStart = LocalDate.of(currentYear, 1, 1).plusDays(periodShift.toLong()),
            baseEnd = LocalDate.of(currentYear, 12, 31).minusDays(periodShift.toLong()),
        )

        val candidates = membershipCandidates.distinctBy { it.id }.shuffled(random).toMutableList()
        if (candidates.isEmpty()) {
            return ContributionSeedResult(
                periods = 2,
                activeMemberships = 0,
                pastMemberships = 0,
                neverMembers = 0,
                paidPreviousYear = 0,
                paidCurrentYear = 0,
                neverPaidMembers = 0,
            )
        }

        var activeTarget = targetCount(candidates.size, config.activeMemberRatio)
        var pastTarget = targetCount(candidates.size, config.pastMemberRatio)
        if (candidates.size >= 2 && pastTarget == 0) {
            pastTarget = 1
        }
        if (candidates.size >= 2 && activeTarget == 0) {
            activeTarget = 1
        }
        if (activeTarget + pastTarget > candidates.size) {
            val overflow = activeTarget + pastTarget - candidates.size
            if (pastTarget > activeTarget) {
                pastTarget = max(0, pastTarget - overflow)
            } else {
                activeTarget = max(0, activeTarget - overflow)
            }
        }
        if (candidates.size >= 3 && activeTarget + pastTarget >= candidates.size) {
            if (pastTarget > 1) {
                pastTarget -= 1
            } else if (activeTarget > 1) {
                activeTarget -= 1
            }
        }

        val activeMembers = candidates.take(activeTarget)
        val pastMembers = candidates.drop(activeTarget).take(pastTarget)
        val neverMembers = candidates.drop(activeTarget + pastTarget)

        activeMembers.forEach { user ->
            persistence.persist(
                Membership(
                    user = user,
                    startDate = currentPeriod.startDate.minusMonths(2),
                    endDate = null,
                    memberType = MemberType.REGULAR,
                    incasso = true,
                )
            )
        }
        pastMembers.forEachIndexed { index, user ->
            persistence.persist(
                Membership(
                    user = user,
                    startDate = previousPeriod.startDate.minusMonths(2),
                    endDate = previousPeriod.endDate.minusDays((index % 30 + 1).toLong()),
                    memberType = MemberType.REGULAR,
                    incasso = index % 2 == 0,
                )
            )
        }

        val membersWithMembership = (activeMembers + pastMembers).shuffled(random)
        if (membersWithMembership.isEmpty()) {
            return ContributionSeedResult(
                periods = 2,
                activeMemberships = activeMembers.size,
                pastMemberships = pastMembers.size,
                neverMembers = neverMembers.size,
                paidPreviousYear = 0,
                paidCurrentYear = 0,
                neverPaidMembers = 0,
            )
        }

        var previousPaidTarget = targetCount(membersWithMembership.size, config.previousYearPaidRatio)
        var currentPaidTarget = targetCount(membersWithMembership.size, config.currentYearPaidRatio)
        if (membersWithMembership.size >= 2 && previousPaidTarget == 0) previousPaidTarget = 1
        if (membersWithMembership.size >= 2 && currentPaidTarget == 0) currentPaidTarget = 1
        if (membersWithMembership.size >= 3) {
            val maxPaidTotal = membersWithMembership.size - 1
            while (previousPaidTarget + currentPaidTarget > maxPaidTotal) {
                if (currentPaidTarget > previousPaidTarget && currentPaidTarget > 1) {
                    currentPaidTarget -= 1
                } else if (previousPaidTarget > 1) {
                    previousPaidTarget -= 1
                } else {
                    break
                }
            }
        } else if (membersWithMembership.size == 2) {
            previousPaidTarget = 1
            currentPaidTarget = 1
        } else if (membersWithMembership.size == 1) {
            previousPaidTarget = 1
            currentPaidTarget = 0
        }

        val previousPaidMembers = membersWithMembership.take(previousPaidTarget)
        val currentPaidMembers = membersWithMembership
            .drop(previousPaidMembers.size)
            .take(currentPaidTarget)

        previousPaidMembers.forEach { user ->
            persistence.persist(
                Contribution(
                    user = user,
                    contributionPeriod = previousPeriod,
                )
            )
        }
        currentPaidMembers.forEach { user ->
            persistence.persist(
                Contribution(
                    user = user,
                    contributionPeriod = currentPeriod,
                )
            )
        }

        return ContributionSeedResult(
            periods = 2,
            activeMemberships = activeMembers.size,
            pastMemberships = pastMembers.size,
            neverMembers = neverMembers.size,
            paidPreviousYear = previousPaidMembers.size,
            paidCurrentYear = currentPaidMembers.size,
            neverPaidMembers = membersWithMembership.size - previousPaidMembers.size - currentPaidMembers.size,
        )
    }

    private fun createContributionPeriodWithRetry(baseStart: LocalDate, baseEnd: LocalDate): ContributionPeriod {
        var attempt = 0L
        while (attempt < 30) {
            val start = baseStart.plusDays(attempt)
            val end = baseEnd.minusDays(attempt)
            if (!end.isAfter(start)) {
                break
            }
            try {
                return contributionFactory.createPeriod(startDate = start, endDate = end)
            } catch (_: RuntimeException) {
                attempt += 1
            }
        }
        error("Unable to seed a unique contribution period based on $baseStart..$baseEnd")
    }

    private fun targetCount(total: Int, ratio: Double): Int {
        if (total <= 0 || ratio <= 0.0) return 0
        return max(1, (total * ratio).roundToInt().coerceAtMost(total))
    }

    private fun buildEventTitle(): String {
        val a = shortNameWord()
        val b = shortNameWord()
        return "$a $b".take(28)
    }

    private fun sanitizeToken(raw: String): String {
        return raw.lowercase()
            .replace(unsafeTokenRegex, "_")
            .trim('_')
            .ifBlank { "seed" }
    }

    private fun nextSuffix(): String {
        sequence += 1
        return "${runToken}${sequence.toString(36)}"
    }

    private fun shortNameWord(): String {
        val word = faker.lorem().word()
            .replace(Regex("[^a-zA-Z]"), "")
            .take(12)
            .lowercase()
            .ifBlank { "seed" }
        return word.replaceFirstChar { it.uppercase() }
    }
}

private data class EventScenario(
    val name: String,
    val count: Int,
    val approved: Boolean,
    val membersOnly: Boolean,
    val past: Boolean,
)

private data class EventSeedResult(
    val seededEvents: Int,
    val withSignUpForms: Int,
    val withBanners: Int,
    val signUps: Int,
)

private data class ContributionSeedResult(
    val periods: Int,
    val activeMemberships: Int,
    val pastMemberships: Int,
    val neverMembers: Int,
    val paidPreviousYear: Int,
    val paidCurrentYear: Int,
    val neverPaidMembers: Int,
)

private data class SeedSummary(
    val members: Int,
    val committeeMembers: Int,
    val guestUsers: Int,
    val boardMembers: Int,
    val admins: Int,
    val configuredUsers: Int,
    val committees: Int,
    val events: Int,
    val eventsWithSignUpForms: Int,
    val eventsWithBanners: Int,
    val eventSignUps: Int,
    val contributionPeriods: Int,
    val activeMemberships: Int,
    val pastMemberships: Int,
    val neverMembers: Int,
    val paidPreviousYear: Int,
    val paidCurrentYear: Int,
    val neverPaidMembers: Int,
)

private data class SeedParsedArgs(
    val configPath: Path?,
    val profile: String?,
    val showHelp: Boolean,
)

private fun parseArgs(args: Array<String>): SeedParsedArgs {
    var configPath: Path? = null
    var profile: String? = null
    var showHelp = false
    var i = 0

    while (i < args.size) {
        when (val arg = args[i]) {
            "--help", "-h" -> {
                showHelp = true
                i += 1
            }

            "--config" -> {
                configPath = Path.of(args.getOrNull(i + 1) ?: error("Missing value for --config"))
                i += 2
            }

            "--profile" -> {
                profile = args.getOrNull(i + 1) ?: error("Missing value for --profile")
                i += 2
            }

            else -> {
                if (arg.startsWith("--config=")) {
                    configPath = Path.of(arg.substringAfter("="))
                    i += 1
                } else if (arg.startsWith("--profile=")) {
                    profile = arg.substringAfter("=")
                    i += 1
                } else {
                    error("Unknown argument: $arg")
                }
            }
        }
    }

    return SeedParsedArgs(configPath = configPath, profile = profile, showHelp = showHelp)
}

private fun printUsage() {
    println("Usage: seedTestDatabase [--config /path/to/database-seeder.yml] [--profile dev]")
    println("When --config is omitted, classpath:$DEFAULT_CONFIG_RESOURCE is used.")
    println("When --profile is omitted, spring.profiles.active / SPRING_PROFILES_ACTIVE is used, falling back to '$DEFAULT_ACTIVE_PROFILE'.")
}

private fun loadConfig(configPath: Path?): SeederConfig {
    val yaml = Yaml()
    val loaded: Any?
    val source: String

    if (configPath != null) {
        source = configPath.toAbsolutePath().toString()
        check(Files.exists(configPath)) { "Config file not found: $source" }
        loaded = Files.newInputStream(configPath).use { input -> yaml.load<Any?>(input) }
    } else {
        source = "classpath:$DEFAULT_CONFIG_RESOURCE"
        val resource = Thread.currentThread().contextClassLoader.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)
            ?: error("Default config not found on classpath: $DEFAULT_CONFIG_RESOURCE")
        loaded = resource.use { input -> yaml.load<Any?>(input) }
    }

    val raw = loaded.asStringMap()
    log.info("Loaded seeding config from {}", source)
    return SeederConfig.fromMap(raw)
}

private data class SeederConfig(
    val users: UserSeedConfig = UserSeedConfig(),
    val organization: OrganizationSeedConfig = OrganizationSeedConfig(),
    val events: EventSeedConfig = EventSeedConfig(),
    val contributions: ContributionSeedConfig = ContributionSeedConfig(),
    val randomSeed: Long = 42L,
) {
    fun normalized(): SeederConfig {
        return copy(
            users = users.normalized(),
            organization = organization.normalized(),
            events = events.normalized(),
            contributions = contributions.normalized(),
            randomSeed = randomSeed,
        )
    }

    companion object {
        fun fromMap(raw: Map<String, Any?>): SeederConfig {
            val usersRaw = raw.child("users")
            val organizationRaw = raw.child("organization")
            val eventsRaw = raw.child("events")
            val contributionsRaw = raw.child("contributions")

            return SeederConfig(
                users = UserSeedConfig(
                    members = usersRaw.int(default = UserSeedConfig().members, "members"),
                    committeeMembers = usersRaw.int(default = UserSeedConfig().committeeMembers, "committeeMembers"),
                    guestUsers = usersRaw.int(default = UserSeedConfig().guestUsers, "guestUsers"),
                    boardMembers = usersRaw.int(default = UserSeedConfig().boardMembers, "boardMembers"),
                    admins = usersRaw.int(default = UserSeedConfig().admins, "admins"),
                    defaultPassword = usersRaw.string(default = UserSeedConfig().defaultPassword, "defaultPassword"),
                    namedUsers = usersRaw.list("namedUsers").mapIndexed { index, rawUser ->
                        val userMap = rawUser.asStringMap()
                        val username = userMap.string(default = "", "username").trim()
                        check(username.isNotBlank()) {
                            "users.namedUsers[$index].username is required"
                        }
                        NamedUserSeedConfig(
                            username = username,
                            role = parseRole(userMap.string(default = Role.GUEST.name, "role"), index),
                            firstName = userMap.stringOrNull("firstName"),
                            lastName = userMap.stringOrNull("lastName"),
                            includeMemberData = userMap.booleanOrNull("includeMemberData"),
                            includeAddress = userMap.booleanOrNull("includeAddress"),
                        )
                    },
                ),
                organization = OrganizationSeedConfig(
                    committees = organizationRaw.int(default = OrganizationSeedConfig().committees, "committees"),
                    boardName = organizationRaw.string(default = OrganizationSeedConfig().boardName, "boardName"),
                    boardCandidate = organizationRaw.string(
                        default = OrganizationSeedConfig().boardCandidate,
                        "boardCandidate"
                    ),
                ),
                events = EventSeedConfig(
                    durationHours = eventsRaw.int(default = EventSeedConfig().durationHours, "durationHours"),
                    pastApprovedPublic = eventsRaw.int(
                        default = EventSeedConfig().pastApprovedPublic,
                        "pastApprovedPublic"
                    ),
                    pastApprovedMembersOnly = eventsRaw.int(
                        default = EventSeedConfig().pastApprovedMembersOnly,
                        "pastApprovedMembersOnly"
                    ),
                    pastUnapprovedPublic = eventsRaw.int(
                        default = EventSeedConfig().pastUnapprovedPublic,
                        "pastUnapprovedPublic"
                    ),
                    pastUnapprovedMembersOnly = eventsRaw.int(
                        default = EventSeedConfig().pastUnapprovedMembersOnly,
                        "pastUnapprovedMembersOnly"
                    ),
                    futureApprovedPublic = eventsRaw.int(
                        default = EventSeedConfig().futureApprovedPublic,
                        "futureApprovedPublic"
                    ),
                    futureApprovedMembersOnly = eventsRaw.int(
                        default = EventSeedConfig().futureApprovedMembersOnly,
                        "futureApprovedMembersOnly"
                    ),
                    futureUnapprovedPublic = eventsRaw.int(
                        default = EventSeedConfig().futureUnapprovedPublic,
                        "futureUnapprovedPublic"
                    ),
                    futureUnapprovedMembersOnly = eventsRaw.int(
                        default = EventSeedConfig().futureUnapprovedMembersOnly,
                        "futureUnapprovedMembersOnly"
                    ),
                    signUpFormRatio = eventsRaw.double(
                        default = EventSeedConfig().signUpFormRatio,
                        "signUpFormRatio"
                    ),
                    bannerRatio = eventsRaw.double(
                        default = EventSeedConfig().bannerRatio,
                        "bannerRatio"
                    ),
                    signUpRatio = eventsRaw.double(
                        default = EventSeedConfig().signUpRatio,
                        "signUpRatio"
                    ),
                ),
                contributions = ContributionSeedConfig(
                    activeMemberRatio = contributionsRaw.double(
                        default = ContributionSeedConfig().activeMemberRatio,
                        "activeMemberRatio"
                    ),
                    pastMemberRatio = contributionsRaw.double(
                        default = ContributionSeedConfig().pastMemberRatio,
                        "pastMemberRatio"
                    ),
                    previousYearPaidRatio = contributionsRaw.double(
                        default = ContributionSeedConfig().previousYearPaidRatio,
                        "previousYearPaidRatio"
                    ),
                    currentYearPaidRatio = contributionsRaw.double(
                        default = ContributionSeedConfig().currentYearPaidRatio,
                        "currentYearPaidRatio"
                    ),
                ),
                randomSeed = raw.long(default = 42L, "randomSeed"),
            )
        }
    }
}

private data class UserSeedConfig(
    val members: Int = 24,
    val committeeMembers: Int = 8,
    val guestUsers: Int = 12,
    val boardMembers: Int = 4,
    val admins: Int = 1,
    val defaultPassword: String = "Password123!",
    val namedUsers: List<NamedUserSeedConfig> = emptyList(),
) {
    fun normalized(): UserSeedConfig {
        val duplicateUsernames = namedUsers
            .groupingBy { it.username }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        check(duplicateUsernames.isEmpty()) {
            "Duplicate usernames in users.namedUsers: ${duplicateUsernames.sorted().joinToString(", ")}"
        }

        return copy(
            members = max(1, members),
            committeeMembers = max(1, committeeMembers),
            guestUsers = max(1, guestUsers),
            boardMembers = max(1, boardMembers),
            admins = max(1, admins),
            defaultPassword = defaultPassword.ifBlank { "Password123!" },
        )
    }
}

private data class NamedUserSeedConfig(
    val username: String,
    val role: Role = Role.GUEST,
    val firstName: String? = null,
    val lastName: String? = null,
    val includeMemberData: Boolean? = null,
    val includeAddress: Boolean? = null,
)

private data class OrganizationSeedConfig(
    val committees: Int = 4,
    val boardName: String = "Seed Board",
    val boardCandidate: String = "Seed Candidate",
) {
    fun normalized(): OrganizationSeedConfig {
        return copy(committees = max(1, committees))
    }
}

private data class EventSeedConfig(
    val durationHours: Int = 3,
    val pastApprovedPublic: Int = 2,
    val pastApprovedMembersOnly: Int = 2,
    val pastUnapprovedPublic: Int = 2,
    val pastUnapprovedMembersOnly: Int = 2,
    val futureApprovedPublic: Int = 2,
    val futureApprovedMembersOnly: Int = 2,
    val futureUnapprovedPublic: Int = 2,
    val futureUnapprovedMembersOnly: Int = 2,
    val signUpFormRatio: Double = 0.45,
    val bannerRatio: Double = 0.35,
    val signUpRatio: Double = 0.25,
) {
    fun normalized(): EventSeedConfig {
        return copy(
            durationHours = max(1, durationHours),
            pastApprovedPublic = max(1, pastApprovedPublic),
            pastApprovedMembersOnly = max(1, pastApprovedMembersOnly),
            pastUnapprovedPublic = max(1, pastUnapprovedPublic),
            pastUnapprovedMembersOnly = max(1, pastUnapprovedMembersOnly),
            futureApprovedPublic = max(1, futureApprovedPublic),
            futureApprovedMembersOnly = max(1, futureApprovedMembersOnly),
            futureUnapprovedPublic = max(1, futureUnapprovedPublic),
            futureUnapprovedMembersOnly = max(1, futureUnapprovedMembersOnly),
            signUpFormRatio = signUpFormRatio.clampRatio(),
            bannerRatio = bannerRatio.clampRatio(),
            signUpRatio = signUpRatio.clampRatio(),
        )
    }
}

private data class ContributionSeedConfig(
    val activeMemberRatio: Double = 0.5,
    val pastMemberRatio: Double = 0.3,
    val previousYearPaidRatio: Double = 0.4,
    val currentYearPaidRatio: Double = 0.35,
) {
    fun normalized(): ContributionSeedConfig {
        return copy(
            activeMemberRatio = activeMemberRatio.clampRatio(),
            pastMemberRatio = pastMemberRatio.clampRatio(),
            previousYearPaidRatio = previousYearPaidRatio.clampRatio(),
            currentYearPaidRatio = currentYearPaidRatio.clampRatio(),
        )
    }
}

private fun Any?.asStringMap(): Map<String, Any?> {
    return (this as? Map<*, *>)?.entries
        ?.associate { (key, value) -> key.toString() to value }
        ?: emptyMap()
}

private fun Map<String, Any?>.child(key: String): Map<String, Any?> {
    return this[key].asStringMap()
}

private fun Map<String, Any?>.list(key: String): List<Any?> {
    return this[key] as? List<Any?> ?: emptyList()
}

private fun Map<String, Any?>.int(default: Int, vararg keys: String): Int {
    val value = firstPresent(keys) ?: return default
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

private fun Map<String, Any?>.long(default: Long, vararg keys: String): Long {
    val value = firstPresent(keys) ?: return default
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }
}

private fun Map<String, Any?>.double(default: Double, vararg keys: String): Double {
    val value = firstPresent(keys) ?: return default
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: default
        else -> default
    }
}

private fun Map<String, Any?>.string(default: String, vararg keys: String): String {
    val value = firstPresent(keys) ?: return default
    return value.toString().takeIf { it.isNotBlank() } ?: default
}

private fun Double.clampRatio(): Double = coerceIn(0.0, 1.0)

private fun Map<String, Any?>.stringOrNull(vararg keys: String): String? {
    val value = firstPresent(keys) ?: return null
    return value.toString().trim().takeIf { it.isNotBlank() }
}

private fun Map<String, Any?>.booleanOrNull(vararg keys: String): Boolean? {
    val value = firstPresent(keys) ?: return null
    return when (value) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

        else -> null
    }
}

private fun Map<String, Any?>.firstPresent(keys: Array<out String>): Any? {
    for (key in keys) {
        if (containsKey(key)) {
            return this[key]
        }
    }
    return null
}

private fun parseRole(rawRole: String, index: Int): Role {
    val normalized = rawRole.trim().uppercase()
    return try {
        Role.valueOf(normalized)
    } catch (_: IllegalArgumentException) {
        error("users.namedUsers[$index].role has invalid value '$rawRole'")
    }
}
