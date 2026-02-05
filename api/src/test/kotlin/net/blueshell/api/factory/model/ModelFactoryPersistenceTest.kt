package net.blueshell.api.factory.model

import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.model.*
import net.blueshell.api.model.board.Board
import net.blueshell.api.model.board.BoardDocument
import net.blueshell.api.model.board.BoardMember
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.api.model.contribution.ContributionReminder
import net.blueshell.api.model.event.*
import net.blueshell.api.model.survey.Answer
import net.blueshell.api.model.survey.Question
import net.blueshell.api.model.survey.Survey
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import org.springframework.transaction.annotation.Transactional
import java.util.function.Consumer

@SpringBootTest
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@Transactional
class ModelFactoryPersistenceTest {

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var addressFactory: AddressFactory

    @Autowired
    private lateinit var answerFactory: AnswerFactory

    @Autowired
    private lateinit var blogFactory: BlogFactory

    @Autowired
    private lateinit var boardDocumentFactory: BoardDocumentFactory

    @Autowired
    private lateinit var boardFactory: BoardFactory

    @Autowired
    private lateinit var boardMemberFactory: BoardMemberFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var committeeMemberFactory: CommitteeMemberFactory

    @Autowired
    private lateinit var contributionFactory: ContributionFactory

    @Autowired
    private lateinit var contributionPeriodFactory: ContributionPeriodFactory

    @Autowired
    private lateinit var contributionReminderFactory: ContributionReminderFactory

    @Autowired
    private lateinit var eventBannerFactory: EventBannerFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var eventFeedbackFactory: EventFeedbackFactory

    @Autowired
    private lateinit var eventPictureFactory: EventPictureFactory

    @Autowired
    private lateinit var eventSignUpAnswerFactory: EventSignUpAnswerFactory

    @Autowired
    private lateinit var eventSignUpFactory: EventSignUpFactory

    @Autowired
    private lateinit var fileFactory: FileFactory

    @Autowired
    private lateinit var guestFactory: GuestFactory

    @Autowired
    private lateinit var membershipFactory: MembershipFactory

    @Autowired
    private lateinit var questionFactory: QuestionFactory

    @Autowired
    private lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @Autowired
    private lateinit var redirectFactory: RedirectFactory

    @Autowired
    private lateinit var sponsorFactory: SponsorFactory

    @Autowired
    private lateinit var surveyFactory: SurveyFactory

    @Autowired
    private lateinit var telemetryFactory: TelemetryFactory

    @Autowired
    private lateinit var userFactory: UserFactory

    private fun <T> persist(entity: T): T = entityManager.merge(entity)

    private fun <T> assertPersisted(type: Class<T>, id: Any?) {
        entityManager.flush()
        entityManager.clear()
        assertNotNull(id, "Expected id for ${type.simpleName}")
        val found = entityManager.find(type, id)
        assertNotNull(found, "Expected ${type.simpleName} to be persisted")
    }

    private fun persistUser(): User {
        val user = userFactory.createBasic()
        return persist(user)
    }

    private fun persistCommittee(): Committee {
        val committee = committeeFactory.createBasic()
        return persist(committee)
    }

    private fun persistContributionPeriod(): ContributionPeriod {
        val period = contributionPeriodFactory.createBasic()
        return persist(period)
    }

    private fun persistSurvey(): Survey {
        val survey = surveyFactory.createBasic()
        return persist(survey)
    }

    private fun fileWithUploader(file: File): File {
        val uploader = persistUser()
        file.uploader = uploader
        return file
    }

    private fun persistEvent(): Event {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee
        event.committeeId = committee.id
        event.signUp = false
        return persist(event)
    }

    private fun persistQuestionWithSurvey(survey: Survey): Question {
        val question = questionFactory.createWithCustomizations {
            it.type = QuestionType.OPEN
            it.choiceLabels = null
            it.survey = survey
        }
        return persist(question)
    }

    private fun persistTextAnswer(question: Question): Answer {
        val answer = answerFactory.createWithCustomizations {
            it.question = question
            it.optionSelections = null
            it.textResponse = "Test answer"
        }
        return persist(answer)
    }

    @Test
    fun addressFactory_creates_persistable_address() {
        val address = addressFactory.createBasic()
        val saved = persist(address)
        assertPersisted(Address::class.java, saved.id)
    }

    @Test
    fun userFactory_creates_persistable_user() {
        val user = userFactory.createBasic()
        val saved = persist(user)
        assertPersisted(User::class.java, saved.id)
    }

    @Test
    fun fileFactory_creates_persistable_file() {
        val file = fileWithUploader(fileFactory.createBasic())
        val saved = persist(file)
        assertPersisted(File::class.java, saved.id)
    }

    @Test
    fun boardFactory_creates_persistable_board() {
        val picture = fileWithUploader(fileFactory.createImage())
        val board = boardFactory.createFull()
        board.picture = picture
        val saved = persist(board)
        assertPersisted(Board::class.java, saved.id)
    }

    @Test
    fun boardMemberFactory_creates_persistable_board_member() {
        val board = persist(boardFactory.createBasic())
        val user = persistUser()
        val picture = fileWithUploader(fileFactory.createImage())

        val member = boardMemberFactory.createBasic(board, user)
        member.picture = persist(picture)

        val saved = persist(member)
        assertPersisted(BoardMember::class.java, saved.id)
    }

    @Test
    fun boardDocumentFactory_creates_persistable_board_document() {
        val board = boardFactory.createBasic()
        val document = fileWithUploader(fileFactory.createDocument())

        val boardDocument = boardDocumentFactory.createBasic()
        boardDocument.board = persist(board)
        boardDocument.file = persist(document)

        val saved = persist(boardDocument)
        assertPersisted(BoardDocument::class.java, saved.id)
    }

    @Test
    fun committeeFactory_creates_persistable_committee() {
        val committee = committeeFactory.createBasic()
        val saved = persist(committee)
        assertPersisted(Committee::class.java, saved.id)
    }

    @Test
    fun committeeMemberFactory_creates_persistable_committee_member() {
        val committee = persistCommittee()
        val user = persistUser()

        val member = committeeMemberFactory.createBasic(user, committee)

        val saved = persist(member)
        assertPersisted(CommitteeMember::class.java, saved.id)
    }

    @Test
    fun membershipFactory_creates_persistable_membership() {
        val user = persistUser()
        val membership = membershipFactory.createBasic(user)
        membership.user = user

        val saved = persist(membership)
        assertPersisted(Membership::class.java, saved.id)
    }

    @Test
    fun contributionPeriodFactory_creates_persistable_contribution_period() {
        val period = contributionPeriodFactory.createBasic()
        val saved = persist(period)
        assertPersisted(ContributionPeriod::class.java, saved.id)
    }

    @Test
    fun contributionFactory_creates_persistable_contribution() {
        val period = persistContributionPeriod()
        val user = persistUser()

        val contribution = contributionFactory.createBasic()
        contribution.user = user
        contribution.contributionPeriod = period

        val saved = persist(contribution)
        assertPersisted(Contribution::class.java, saved.id)
    }

    @Test
    fun contributionReminderFactory_creates_persistable_contribution_reminder() {
        val period = persistContributionPeriod()
        val user = persistUser()

        val reminder = contributionReminderFactory.createBasic()
        reminder.user = user
        reminder.contributionPeriod = period

        val saved = persist(reminder)
        assertPersisted(ContributionReminder::class.java, saved.id)
    }

    @Test
    fun blogFactory_creates_persistable_blog() {
        val blog = blogFactory.createBasic()
        val saved = persist(blog)
        assertPersisted(Blog::class.java, saved.id)
    }

    @Test
    fun sponsorFactory_creates_persistable_sponsor() {
        val logo = fileWithUploader(fileFactory.createImage())
        val sponsor = sponsorFactory.createBasic()
        sponsor.picture = persist(logo)

        val saved = persist(sponsor)
        assertPersisted(Sponsor::class.java, saved.id)
    }

    @Test
    fun telemetryFactory_creates_persistable_telemetry() {
        val telemetry = telemetryFactory.createBasic()
        val saved = persist(telemetry)
        assertPersisted(Telemetry::class.java, saved.id)
    }

    @Test
    fun redirectFactory_creates_persistable_redirect() {
        val telemetry = persist(telemetryFactory.createBasic())
        val redirect = redirectFactory.createBasic()
        redirect.telemetry = telemetry

        val saved = persist(redirect)
        assertPersisted(Redirect::class.java, saved.id)
    }

    @Test
    fun recoveryTokenFactory_creates_persistable_recovery_token() {
        val user = persistUser()
        val token = recoveryTokenFactory.createBasic()
        token.user = user

        val saved = persist(token)
        assertPersisted(RecoveryToken::class.java, saved.id)
    }

    @Test
    fun guestFactory_creates_persistable_guest() {
        val guest = guestFactory.createBasic()
        val saved = persist(guest)
        assertPersisted(Guest::class.java, saved.id)
    }

    @Test
    fun surveyFactory_creates_persistable_survey() {
        val survey = surveyFactory.createBasic()
        val saved = persist(survey)
        assertPersisted(Survey::class.java, saved.id)
    }

    @Test
    fun questionFactory_creates_persistable_question_for_survey() {
        val survey = persistSurvey()
        val question = questionFactory.createForSurvey(survey)
        question.survey = persist(survey)

        val saved = persist(question)
        assertPersisted(Question::class.java, saved.id)
    }

    @Test
    fun answerFactory_creates_persistable_answer() {
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)
        assertPersisted(Answer::class.java, answer.id)
    }

    @Test
    fun eventFactory_creates_persistable_event() {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee
        event.committeeId = committee.id

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }

    @Test
    fun eventBannerFactory_creates_persistable_event_banner() {
        val event = persistEvent()
        var bannerFile = fileWithUploader(fileFactory.createImage())
        bannerFile = persist(bannerFile)

        val banner = eventBannerFactory.createBasic()
        banner.event = event
        banner.file = bannerFile

        val saved = persist(banner)
        assertPersisted(EventBanner::class.java, saved.id)
    }

    @Test
    fun eventPictureFactory_creates_persistable_event_picture() {
        val event = persistEvent()
        val pictureFile = fileWithUploader(fileFactory.createImage())

        val picture = eventPictureFactory.createBasic()
        picture.event = event
        picture.picture = persist(pictureFile)

        val saved = persist(picture)
        assertPersisted(EventPicture::class.java, saved.id)
    }

    @Test
    fun eventFeedbackFactory_creates_persistable_event_feedback() {
        val event = persistEvent()
        val feedback = eventFeedbackFactory.createBasic()
        feedback.event = event

        val saved = persist(feedback)
        assertPersisted(EventFeedback::class.java, saved.id)
    }

    @Test
    fun eventSignUpFactory_creates_persistable_event_sign_up() {
        val event = persistEvent()
        val user = persistUser()
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)

        val signUp = eventSignUpFactory.createBasic()
        signUp.event = event
        signUp.user = user
        val answers = signUp.answers as MutableSet<Answer>
        answers.clear()
        answers.add(answer)

        val saved = persist(signUp)
        assertPersisted(EventSignUp::class.java, saved.id)
    }

    @Test
    fun eventSignUpAnswerFactory_creates_persistable_event_sign_up_answer() {
        val event = persistEvent()
        val user = persistUser()
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)

        val signUp = eventSignUpFactory.createBasic()
        signUp.event = event
        signUp.user = user
        signUp.userId = user.id
        val signUpAnswers = signUp.answers as MutableSet<Answer>
        signUpAnswers.clear()
        val savedSignUp = persist(signUp)

        val signUpAnswer = eventSignUpAnswerFactory.createBasic()
        signUpAnswer.answer = answer
        signUpAnswer.eventSignUp = savedSignUp

        val savedAnswer = persist(signUpAnswer)
        assertPersisted(EventSignUpAnswer::class.java, savedAnswer.id)
    }

    @Test
    fun addressFactory_applies_customizer() {
        val customizer = mockk<Consumer<Address>>(relaxed = true)
        addressFactory.createWithCustomizations(customizer)
        verify(exactly = 1) { customizer.accept(any()) }
    }
}
