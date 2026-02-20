package net.blueshell.api.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.factory.blog.persistence.BlogFactory
import net.blueshell.api.factory.board.persistence.BoardFactory
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.file.persistence.FileFactory
import net.blueshell.api.factory.job.persistence.JobExecutionFactory
import net.blueshell.api.factory.sponsor.persistence.SponsorFactory
import net.blueshell.api.factory.telemetry.persistence.TelemetryFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.infrastructure.security.JwtTokenGenerator
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipalMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
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
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var springSecurityFilterChain: FilterChainProxy

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

    @Autowired
    protected lateinit var userFactory: UserFactory

    @Autowired
    protected lateinit var blogFactory: BlogFactory

    @Autowired
    protected lateinit var boardFactory: BoardFactory

    @Autowired
    protected lateinit var committeeFactory: CommitteeFactory

    @Autowired
    protected lateinit var eventFactory: EventFactory

    @Autowired
    protected lateinit var contributionFactory: ContributionFactory

    @Autowired
    protected lateinit var fileFactory: FileFactory

    @Autowired
    protected lateinit var sponsorFactory: SponsorFactory

    @Autowired
    protected lateinit var telemetryFactory: TelemetryFactory

    @Autowired
    protected lateinit var jobExecutionFactory: JobExecutionFactory

    @Value("\${app.frontend-url}")
    protected lateinit var frontendUrl: String

    @Value("\${app.url}")
    protected lateinit var appUrl: String

    @BeforeEach
    fun configureMockMvcDefaultCsrf() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(springSecurityFilterChain)
        builder.defaultRequest<DefaultMockMvcBuilder>(get("/").with(csrfToken()))
        mvc = builder.build()
    }

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

    protected fun csrfToken(): RequestPostProcessor {
        return csrf().asHeader()
    }

    /**
     * Creates and persists a user with specific role.
     */
    protected fun createUserWithRole(role: Role, enabled: Boolean = true): User {
        return userFactory.createUserWithRole(role, enabled)
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
        return blogFactory.create(title, html, publishedAt)
    }

    protected fun createBoardFixture(
        name: String = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): Board {
        return boardFactory.create(name, candidate, startDate)
    }

    protected fun addBoardMember(board: Board, user: User, role: String = "CHAIR"): Board {
        val member = boardFactory.buildMember(board, user, role)
        board.addMember(member)
        return persist(board)
    }

    protected fun createCommitteeFixture(
        name: String = "Committee ${System.currentTimeMillis()}",
        description: String = "Committee description"
    ): Committee {
        return committeeFactory.create(name, description)
    }

    protected fun addCommitteeMember(committee: Committee, user: User, role: String = "Member"): Committee {
        val member = committeeFactory.buildMember(committee, user, role)
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
        return eventFactory.create(committee, approved, membersOnly, signUp, title)
    }

    protected fun createAddressFixture(
        user: User = createUserWithRole(Role.MEMBER),
        city: String = "Enschede",
        country: String = "NL"
    ): Address {
        val address = userFactory.buildAddress(user = user, country = country, city = city)

        val persistedUser = assignAddress(user, address)
        return refreshUser(persistedUser).address!!
    }

    protected fun assignAddress(user: User, address: Address = userFactory.buildAddress(user = user)): User {
        user.replaceAddress(address)
        return persist(user)
    }

    protected fun assignMemberProfile(user: User): User {
        val profile = userFactory.buildMemberProfile(user)

        user.replaceMemberProfile(profile)
        return persist(user)
    }

    protected fun createMembershipFixture(
        user: User = createUserWithRole(Role.MEMBER),
        memberType: MemberType = MemberType.REGULAR
    ): Membership {
        return userFactory.createMembership(user, memberType)
    }

    protected fun createContributionPeriodFixture(
        startDate: LocalDate = LocalDate.now().minusMonths(1),
        endDate: LocalDate = LocalDate.now().plusMonths(1)
    ): ContributionPeriod {
        return contributionFactory.createPeriod(startDate, endDate)
    }

    protected fun createFileFixture(
        uploader: User = createUserWithRole(Role.BOARD),
        name: String = "banner.png",
        mediaType: String = "image/png",
        type: FileType = FileType.EVENT_BANNER
    ): File {
        return fileFactory.create(uploader, name, mediaType, type)
    }

    protected fun attachEventBanner(event: Event, file: File = createFileFixture()): Event {
        event.banner = eventFactory.buildBanner(event, file)
        return persist(event)
    }

    protected fun createEventSignUpFixture(
        event: Event = createEventFixture(),
        user: User? = createUserWithRole(Role.MEMBER),
        guest: Guest? = null
    ): EventSignUp {
        return eventFactory.createSignUp(event, user, guest)
    }

    protected fun createGuestFixture(
        name: String = "Guest User",
        accessToken: String = "guest-token-${System.currentTimeMillis()}"
    ): Guest {
        return eventFactory.createGuest(name, accessToken)
    }

    protected fun createSponsorFixture(name: String = "Sponsor ${System.currentTimeMillis()}"): Sponsor {
        val uploader = createUserWithRole(Role.BOARD)
        val picture = createFileFixture(uploader = uploader, type = FileType.SPONSOR_PICTURE)
        return sponsorFactory.create(name, picture)
    }

    protected fun createTelemetryFixture(
        platform: PlatformType = PlatformType.TWITTER,
        url: String = "https://example.com/${System.currentTimeMillis()}"
    ): Telemetry {
        return telemetryFactory.create(platform, url)
    }

    protected fun createJobExecutionFixture(jobType: String = "test-job"): JobExecution {
        return jobExecutionFactory.create(jobType)
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
