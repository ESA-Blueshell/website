package net.blueshell.api.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.PersonDetails
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.infrastructure.security.JwtTokenGenerator
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipalMapper
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Date
import java.time.Instant
import java.time.LocalDate

/**
 * Base class for controller integration tests involving users.
 *
 * Provides:
 * - MockMvc for HTTP testing
 * - User repository and password encoder
 * - JWT token generation for authentication
 * - Email mock for verification
 * - Helper methods for user management
 */
@AutoConfigureMockMvc
abstract class UserTestSupport : ServiceTestSupport() {

    @Autowired
    protected lateinit var mvc: MockMvc

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    protected lateinit var tokenGenerator: JwtTokenGenerator

    @Autowired
    protected lateinit var mapper: ObjectMapper

    @Autowired
    protected lateinit var mailSender: MockJavaMailSender

    @Value("\${app.frontend-url}")
    protected lateinit var frontendUrl: String

    @Value("\${app.url}")
    protected lateinit var appUrl: String

    /**
     * Creates bearer token authentication for a user.
     */
    protected fun bearer(user: User): RequestPostProcessor {
        val principal = UserPrincipalMapper.fromUser(user)
        val token = tokenGenerator.generateToken(principal.username)
        return RequestPostProcessor { request ->
            request.addHeader("Authorization", "Bearer $token")
            request
        }
    }

    /**
     * Creates and persists a user with specific role.
     */
    protected fun createUserWithRole(role: Role, enabled: Boolean = true): User {
        val username = "user_${role.name.lowercase()}_${System.currentTimeMillis()}"
        val user = User(
            username = username,
            email = "$username@test.com",
            password = passwordEncoder.encode("Password123!"),
            initials = "TU",
            firstName = "Test",
            lastName = role.name,
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.roles = mutableSetOf(role)
        user.enabled = enabled
        return userRepository.save(user)
    }

    /**
     * Refreshes user from database.
     */
    protected fun refreshUser(user: User): User {
        return transactionTemplate.execute {
            entityManager.flush()
            entityManager.clear()
            userRepository.findById(user.id!!).orElseThrow()
        }!!
    }

    protected fun createBlogFixture(
        title: String = "Blog ${System.currentTimeMillis()}",
        html: String = "<p>Content</p>",
        publishedAt: Instant = Instant.now()
    ): Blog {
        return persist(
            Blog().apply {
                this.title = title
                this.html = html
                this.publishedAt = publishedAt
            }
        )
    }

    protected fun createBoardFixture(
        name: String = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): Board {
        return persist(
            Board().apply {
                this.name = name
                this.candidate = candidate
                this.startDate = startDate
            }
        )
    }

    protected fun addBoardMember(board: Board, user: User, role: String = "CHAIR"): Board {
        val member = BoardMember().apply {
            id = BoardMember.Id(board.id, user.id)
            this.board = board
            this.user = user
            this.role = role
            this.startDate = LocalDate.now().minusDays(1)
        }
        board.addMember(member)
        return persist(board)
    }

    protected fun createCommitteeFixture(
        name: String = "Committee ${System.currentTimeMillis()}",
        description: String = "Committee description"
    ): Committee {
        return persist(
            Committee().apply {
                this.name = name
                this.description = description
            }
        )
    }

    protected fun addCommitteeMember(committee: Committee, user: User, role: String = "Member"): Committee {
        val member = CommitteeMember().apply {
            id = CommitteeMember.Id(committee.id, user.id)
            this.committee = committee
            this.user = user
            this.role = role
        }
        committee.replaceMembers(committee.members + member)
        return persist(committee)
    }

    protected fun createEventFixture(
        committee: Committee = createCommitteeFixture(),
        approved: Boolean = true,
        membersOnly: Boolean = false,
        signUp: Boolean = true,
        title: String = "Event ${System.currentTimeMillis()}"
    ): Event {
        return persist(
            Event().apply {
                this.committee = committee
                this.title = title
                this.description = "Event description"
                this.location = "Campus"
                this.startTime = Instant.now().plusSeconds(3600)
                this.endTime = Instant.now().plusSeconds(7200)
                this.approved = approved
                this.membersOnly = membersOnly
                this.signUp = signUp
            }
        )
    }

    protected fun createAddressFixture(
        user: User = createUserWithRole(Role.MEMBER),
        city: String = "Enschede",
        country: String = "NL"
    ): Address {
        val address = Address(
            user = user,
            country = country,
            city = city,
            street = "Street",
            houseNumber = "1",
            zipCode = "1234AB"
        )

        val persistedUser = assignAddress(user, address)
        return refreshUser(persistedUser).address!!
    }

    protected fun assignAddress(user: User, address: Address = Address(
        user = user,
        country = "NL",
        city = "Enschede",
        street = "Street",
        houseNumber = "1",
        zipCode = "1234AB"
    )): User {
        user.replaceAddress(address)
        return persist(user)
    }

    protected fun assignCompletePersonDetails(user: User): User {
        val profile = PersonDetails(
            user = user,
            dateOfBirth = Date.valueOf(LocalDate.of(1998, 5, 5)),
            studentNumber = "s${System.currentTimeMillis()}",
            gender = "X",
            photoConsent = true,
            bhv = false,
            ehbo = false,
            nationality = "NL"
        )

        user.replacePersonDetails(profile)
        return persist(user)
    }

    protected fun createMembershipFixture(
        user: User = createUserWithRole(Role.MEMBER),
        memberType: MemberType = MemberType.REGULAR
    ): Membership {
        return persist(
            Membership().apply {
                this.user = user
                this.memberType = memberType
                this.startDate = LocalDate.now().minusDays(30)
                this.endDate = null
                this.incasso = true
            }
        )
    }

    protected fun createContributionPeriodFixture(
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate = LocalDate.now().plusMonths(1)
    ): ContributionPeriod {
        return persist(
            ContributionPeriod().apply {
                this.startDate = startDate
                this.endDate = endDate
                this.halfYearFee = 25.0
                this.fullYearFee = 45.0
                this.alumniFee = 10.0
            }
        )
    }

    protected fun createFileFixture(
        uploader: User = createUserWithRole(Role.BOARD),
        name: String = "banner.png",
        mediaType: String = "image/png",
        type: FileType = FileType.EVENT_BANNER
    ): File {
        val path = Path.of("/tmp", "$name-${System.currentTimeMillis()}")
        Files.writeString(path, "test-file")
        return persist(
            File().apply {
                this.name = name
                this.path = path.toString()
                this.uploader = uploader
                this.mediaType = mediaType
                this.size = 1024
                this.type = type
            }
        )
    }

    protected fun attachEventBanner(event: Event, file: File = createFileFixture()): Event {
        event.banner = EventBanner().apply {
            this.event = event
            this.id = EventBanner.Id(event.id, file.id)
            this.fileId = file.id!!
        }
        return persist(event)
    }

    protected fun createEventSignUpFixture(
        event: Event = createEventFixture(),
        user: User? = createUserWithRole(Role.MEMBER),
        guest: Guest? = null
    ): EventSignUp {
        return persist(
            EventSignUp().apply {
                this.event = event
                this.user = user
                this.userId = user?.id
                this.guest = guest
            }
        )
    }

    protected fun createGuestFixture(
        name: String = "Guest User",
        accessToken: String = "guest-token-${System.currentTimeMillis()}"
    ): Guest {
        return persist(
            Guest().apply {
                this.name = name
                this.discord = "guest#1234"
                this.email = "guest-${System.currentTimeMillis()}@example.com"
                this.phoneNumber = "+31612345678"
                this.accessToken = accessToken
            }
        )
    }

    protected fun createSponsorFixture(name: String = "Sponsor ${System.currentTimeMillis()}"): Sponsor {
        val uploader = createUserWithRole(Role.BOARD)
        return persist(
            Sponsor().apply {
                this.name = name
                this.description = "Sponsor description"
                this.picture = createFileFixture(uploader = uploader, type = FileType.SPONSOR_PICTURE)
            }
        )
    }

    protected fun createTelemetryFixture(
        platform: PlatformType = PlatformType.TWITTER,
        url: String = "https://example.com/${System.currentTimeMillis()}"
    ): Telemetry {
        return persist(Telemetry(platform = platform, url = url))
    }

    protected fun createJobExecutionFixture(jobType: String = "test-job"): JobExecution {
        return persist(
            JobExecution(
                jobType = jobType,
                status = JobExecutionStatus.QUEUED,
                payload = """{"key":"value"}"""
            )
        )
    }

    /**
     * Asserts that an email was sent with specific criteria.
     */
    protected fun assertEmailSent(
        toEmail: String,
        subject: String,
        bodyContains: String,
        timeoutMs: Long = 2000
    ) {
        val message = awaitEmail(toEmail, subject, bodyContains, timeoutMs)
        val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
        val body = messageBody(message)

        assertThat(recipients)
            .describedAs("Email recipients should contain $toEmail")
            .contains(toEmail)
        assertThat(message.subject)
            .describedAs("Email subject should be: $subject")
            .isEqualTo(subject)
        assertThat(body)
            .describedAs("Email body should contain: $bodyContains")
            .contains(bodyContains)
    }

    /**
     * Waits for email matching criteria with timeout and polling.
     */
    private fun awaitEmail(
        toEmail: String,
        subject: String,
        bodyContains: String,
        timeoutMs: Long = 2000,
        pollMs: Long = 50
    ): MimeMessage {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val message = findMatchingEmail(toEmail, subject, bodyContains)
            if (message != null) return message
            Thread.sleep(pollMs)
        }
        val message = findMatchingEmail(toEmail, subject, bodyContains)
        checkNotNull(message) {
            "Expected email not found within ${timeoutMs}ms. " +
                    "to=$toEmail, subject=$subject, bodyContains=$bodyContains"
        }
        return message
    }

    /**
     * Finds email in outbox matching criteria.
     */
    private fun findMatchingEmail(toEmail: String, subject: String, bodyContains: String): MimeMessage? {
        return mailSender.outbox.firstOrNull { message ->
            val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
            val body = messageBody(message)
            recipients.contains(toEmail) && message.subject == subject && body.contains(bodyContains)
        }
    }

    /**
     * Extracts text body from MIME message.
     */
    private fun messageBody(message: MimeMessage): String {
        return when (val content = message.content) {
            is String -> content
            is Multipart -> extractFromMultipart(content)
            else -> content.toString()
        }
    }

    private fun extractFromMultipart(multipart: Multipart): String {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            val content = extractFromPart(part)
            if (content != null) return content
        }
        return ""
    }

    private fun extractFromPart(part: Part): String? {
        return when (val content = part.content) {
            is String -> content
            is Multipart -> extractFromMultipart(content)
            else -> null
        }
    }
}
