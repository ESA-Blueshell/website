package net.blueshell.tools

import net.blueshell.api.ApiApplication
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.factory.board.persistence.BoardFactory
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.security.crypto.password.PasswordEncoder
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.random.Random

private val log = LoggerFactory.getLogger("DatabaseSeedTool")
private const val DEFAULT_CONFIG_RESOURCE = "database-seeder.yml"

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (parsed.showHelp) {
        printUsage()
        return
    }

    val loadedConfig = loadConfig(parsed.configPath).normalized()

    val context = SpringApplicationBuilder(ApiApplication::class.java)
        .profiles("test")
        .web(WebApplicationType.NONE)
        .properties(
            "spring.main.banner-mode=off",
            "spring.main.log-startup-info=false",
            "spring.rabbitmq.dynamic=false",
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "spring.rabbitmq.listener.direct.auto-startup=false",
        )
        .run()

    context.use {
        val seeder = DatabaseSeedRunner(
            userFactory = it.getBean<UserFactory>(),
            committeeFactory = it.getBean<CommitteeFactory>(),
            boardFactory = it.getBean<BoardFactory>(),
            eventFactory = it.getBean<EventFactory>(),
            persistence = it.getBean<FactoryPersistenceSupport>(),
            passwordEncoder = it.getBean<PasswordEncoder>(),
        )
        val summary = seeder.seed(loadedConfig)
        log.info("Database seeding completed: {}", summary)
    }
}

private class DatabaseSeedRunner(
    private val userFactory: UserFactory,
    private val committeeFactory: CommitteeFactory,
    private val boardFactory: BoardFactory,
    private val eventFactory: EventFactory,
    private val persistence: FactoryPersistenceSupport,
    private val passwordEncoder: PasswordEncoder,
) {
    private val runTag = System.currentTimeMillis()
    private var sequence = 0L

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

        seedCommitteeMemberships(committees, committeeMembers)
        seedBoard(config, boardMembers, admins)
        val seededEvents = seedEvents(config.events, committees, random)

        return SeedSummary(
            members = members.size,
            committeeMembers = committeeMembers.size,
            guestUsers = guests.size,
            boardMembers = boardMembers.size,
            admins = admins.size,
            configuredUsers = configuredUsers.size,
            committees = committees.size,
            events = seededEvents,
        )
    }

    private fun createCommittees(count: Int): List<Committee> {
        return (1..count).map { index ->
            committeeFactory.create(
                name = "Seed Committee $index [$runTag]",
                description = "Generated committee $index for seeded test data",
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
        return (1..count).map { index ->
            val suffix = nextSuffix()
            val user = userFactory.buildUserWithRole(role, enabled = true).apply {
                username = "${usernamePrefix}_${suffix}_$index"
                email = "${usernamePrefix}.${suffix}.${index}@example.test"
                discord = "${usernamePrefix}_${suffix}_${index}"
                firstName = usernamePrefix.replaceFirstChar { it.uppercase() }
                lastName = role.name.lowercase().replaceFirstChar { it.uppercase() }
                phoneNumber = "06${suffix.takeLast(8).padStart(8, '0')}"
                password = passwordEncoder.encode(defaultPassword)
            }

            val persisted = persistence.persist(user)
            if (includeMemberData) {
                enrichMemberData(persisted, suffix)
            }
            persisted
        }
    }

    private fun createConfiguredUsers(config: UserSeedConfig): List<User> {
        return config.namedUsers.mapIndexed { index, named ->
            val suffix = nextSuffix()
            val user = userFactory.buildUserWithRole(named.role, enabled = true).apply {
                username = named.username
                email = "named.${runTag}.${index + 1}@example.test"
                discord = "named_${runTag}_${index + 1}"
                firstName = named.firstName ?: named.username
                lastName = named.lastName ?: named.role.name.lowercase().replaceFirstChar { it.uppercase() }
                phoneNumber = "06${suffix.takeLast(8).padStart(8, '0')}"
                password = passwordEncoder.encode(config.defaultPassword)
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
            val address = userFactory.buildAddress(
                user = user,
                city = "Enschede",
                street = "Seed Street",
                houseNumber = suffix.takeLast(3).padStart(3, '0'),
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

        val updated = persistence.persist(user)
        if (includeMemberData) {
            userFactory.createMembership(updated)
        }
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
            name = "${config.organization.boardName} [$runTag]",
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
        random: Random,
    ): Int {
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

        var seeded = 0
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
                    title = "${scenario.name} #${index + 1} [$runTag]",
                ).apply {
                    startTime = start
                    endTime = start.plus(config.durationHours.toLong(), ChronoUnit.HOURS)
                    memberPrice = 5.0 + (index % 3)
                    publicPrice = if (membersOnly) null else 10.0 + (index % 4)
                }
                persistence.persist(event)
                seeded++
            }
        }
        return seeded
    }

    private fun nextSuffix(): String {
        sequence += 1
        return "${runTag}${sequence}"
    }
}

private data class EventScenario(
    val name: String,
    val count: Int,
    val approved: Boolean,
    val membersOnly: Boolean,
    val past: Boolean,
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
)

private data class SeedParsedArgs(
    val configPath: Path?,
    val showHelp: Boolean,
)

private fun parseArgs(args: Array<String>): SeedParsedArgs {
    var configPath: Path? = null
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

            else -> {
                if (arg.startsWith("--config=")) {
                    configPath = Path.of(arg.substringAfter("="))
                    i += 1
                } else {
                    error("Unknown argument: $arg")
                }
            }
        }
    }

    return SeedParsedArgs(configPath = configPath, showHelp = showHelp)
}

private fun printUsage() {
    println("Usage: seedTestDatabase [--config /path/to/database-seeder.yml]")
    println("When --config is omitted, classpath:$DEFAULT_CONFIG_RESOURCE is used.")
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
    val randomSeed: Long = 42L,
) {
    fun normalized(): SeederConfig {
        return copy(
            users = users.normalized(),
            organization = organization.normalized(),
            events = events.normalized(),
            randomSeed = randomSeed,
        )
    }

    companion object {
        fun fromMap(raw: Map<String, Any?>): SeederConfig {
            val usersRaw = raw.child("users")
            val organizationRaw = raw.child("organization")
            val eventsRaw = raw.child("events")

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

private fun Map<String, Any?>.string(default: String, vararg keys: String): String {
    val value = firstPresent(keys) ?: return default
    return value.toString().takeIf { it.isNotBlank() } ?: default
}

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
