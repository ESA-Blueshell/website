package net.blueshell.api.testsupport

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.factory.blog.persistence.BlogFactory
import net.blueshell.api.factory.board.persistence.BoardFactory
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.file.persistence.FileFactory
import net.blueshell.api.factory.job.persistence.JobExecutionFactory
import net.blueshell.api.factory.sponsor.persistence.SponsorFactory
import net.blueshell.api.factory.email.persistence.EmailFactory
import net.blueshell.api.factory.telemetry.persistence.TelemetryFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.file.persistence.File
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import net.blueshell.api.security.JwtTokenGenerator
import net.blueshell.api.platform.integration.mock.InMemoryEmailClient
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipalMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import net.blueshell.api.file.api.PublicFileUrls
import com.jayway.jsonpath.JsonPath

abstract class UserTestSupport : ServiceTestSupport() {

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
    protected lateinit var emailTransportClient: InMemoryEmailClient

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

    @Autowired
    protected lateinit var emailFactory: EmailFactory

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

    @BeforeEach
    fun resetEmailClient() {
        emailTransportClient.reset()
    }

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

    protected fun createUserWithRole(role: Role, enabled: Boolean = true): User {
        return userFactory.createUserWithRole(role, enabled)
    }

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
        name: String? = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1),
        number: Int? = null,
    ): Board {
        return number
            ?.let { boardFactory.create(name, candidate, startDate, it) }
            ?: boardFactory.create(name, candidate, startDate)
    }

    protected fun addBoardMember(board: Board, user: User, role: String = "CHAIR"): Board {
        val member = boardFactory.buildMember(board, user, role)
        board.addMember(member)
        return persist(board)
    }

    /** A place held by somebody with no account, which is most of the association's history. */
    protected fun addBoardMemberWithoutAccount(
        board: Board,
        displayName: String,
        role: String = "CHAIR",
    ): BoardMember {
        val member = boardFactory.buildMember(board, user = null, role = role, displayName = displayName)
        board.addMember(member)
        return persist(board).members.first { it.displayName == displayName }
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
        title: String = "Event ${System.currentTimeMillis()}",
        signUpDeadline: Instant? = null,
        signUpLimit: Int? = null
    ): Event {
        return eventFactory.create(committee, approved, membersOnly, signUp, title, signUpDeadline, signUpLimit)
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
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.now().minusDays(30),
        endDate: LocalDate? = null
    ): Membership {
        return userFactory.createMembership(user, memberType, startDate, endDate)
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

    protected fun createJobExecutionFixture(
        jobType: String = "test-job",
        status: JobExecutionStatus = JobExecutionStatus.QUEUED
    ): JobExecution {
        return jobExecutionFactory.create(jobType, status)
    }

    protected fun assertEmailSent(
        toEmail: String,
        subject: String,
        bodyContains: String,
        timeoutMs: Long = 2000
    ) {
        val email = emailTransportClient.sentEmails.firstOrNull { sent ->
            sent.toEmail == toEmail && sent.subject == subject && sent.htmlContent.contains(bodyContains)
        }

        assertThat(email)
            .describedAs("Expected email to=$toEmail subject='$subject' bodyContains='$bodyContains'")
            .isNotNull

        assertThat(email!!.toEmail)
            .describedAs("Email recipients should contain $toEmail")
            .isEqualTo(toEmail)
        assertThat(email.subject)
            .describedAs("Email subject should be: $subject")
            .isEqualTo(subject)
        assertThat(email.htmlContent)
            .describedAs("Email body should contain: $bodyContains")
            .contains(bodyContains)
    }

    /**
     * A picture the converter will accept, which is the smallest thing that really is one.
     *
     * Here rather than in one suite because every test that needs a stored picture needs the
     * same one, and the encoder is real in these tests rather than doubled.
     *
     * 64 square by default, and a size is worth asking for only when the test is about the
     * narrower copies a picture is stored at: nothing is upscaled, so a 64-pixel picture is
     * stored at no width a banner lists and carries no copies at all.
     */
    protected fun picture(
        name: String = "picture.png",
        width: Int = 64,
        height: Int = 64,
    ): MockMultipartFile {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        return MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, bytes)
    }

    /**
     * A picture of the given kind, stored the way a picker stores one, and where it landed.
     *
     * Goes through the upload endpoint rather than the service behind it: what a caller can
     * observe is a stored path, and a save later names exactly that.
     */
    protected fun storedPicture(
        uploader: User,
        kind: FileType,
        width: Int = 64,
        height: Int = 64,
    ): String {
        val stored = mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(picture(width = width, height = height))
                .param("type", kind.name)
                .with(bearer(uploader)).with(csrfToken()),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return JsonPath.read<String>(stored, "$.path")
    }
}
