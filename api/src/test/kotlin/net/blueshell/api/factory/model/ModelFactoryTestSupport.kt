package net.blueshell.api.factory.model

import jakarta.persistence.EntityManager
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.factory.model.board.BoardDocumentFactory
import net.blueshell.api.factory.model.board.BoardFactory
import net.blueshell.api.factory.model.board.BoardMemberFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.committee.CommitteeMemberFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.factory.model.contribution.ContributionReminderFactory
import net.blueshell.api.factory.model.event.*
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.file.persistence.File
import net.blueshell.api.platform.config.TruncateTestDatabaseListener
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@Transactional
abstract class ModelFactoryTestSupport {

    @Autowired
    protected lateinit var entityManager: EntityManager

    @Autowired
    protected lateinit var addressFactory: AddressFactory

    @Autowired
    protected lateinit var answerFactory: AnswerFactory

    @Autowired
    protected lateinit var blogFactory: BlogFactory

    @Autowired
    protected lateinit var boardDocumentFactory: BoardDocumentFactory

    @Autowired
    protected lateinit var boardFactory: BoardFactory

    @Autowired
    protected lateinit var boardMemberFactory: BoardMemberFactory

    @Autowired
    protected lateinit var committeeFactory: CommitteeFactory

    @Autowired
    protected lateinit var committeeMemberFactory: CommitteeMemberFactory

    @Autowired
    protected lateinit var contributionFactory: ContributionFactory

    @Autowired
    protected lateinit var contributionPeriodFactory: ContributionPeriodFactory

    @Autowired
    protected lateinit var contributionReminderFactory: ContributionReminderFactory

    @Autowired
    protected lateinit var eventBannerFactory: EventBannerFactory

    @Autowired
    protected lateinit var eventFactory: EventFactory

    @Autowired
    protected lateinit var eventFeedbackFactory: EventFeedbackFactory

    @Autowired
    protected lateinit var eventPictureFactory: EventPictureFactory

    @Autowired
    protected lateinit var eventSignUpAnswerFactory: EventSignUpAnswerFactory

    @Autowired
    protected lateinit var eventSignUpFactory: EventSignUpFactory

    @Autowired
    protected lateinit var fileFactory: FileFactory

    @Autowired
    protected lateinit var guestFactory: GuestFactory

    @Autowired
    protected lateinit var membershipFactory: MembershipFactory

    @Autowired
    protected lateinit var questionFactory: QuestionFactory

    @Autowired
    protected lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @Autowired
    protected lateinit var redirectFactory: RedirectFactory

    @Autowired
    protected lateinit var sponsorFactory: SponsorFactory

    @Autowired
    protected lateinit var surveyFactory: SurveyFactory

    @Autowired
    protected lateinit var telemetryFactory: TelemetryFactory

    @Autowired
    protected lateinit var userFactory: UserFactory

    protected fun <T> persist(entity: T): T = entityManager.merge(entity)

    protected fun <T> assertPersisted(type: Class<T>, id: Any?) {
        entityManager.flush()
        entityManager.clear()
        assertNotNull(id, "Expected id for ${type.simpleName}")
        val found = entityManager.find(type, id)
        assertNotNull(found, "Expected ${type.simpleName} to be persisted")
    }

    protected fun persistUser(): User {
        val user = userFactory.createBasic()
        return persist(user)
    }

    protected fun persistCommittee(): Committee {
        val committee = committeeFactory.createBasic()
        return persist(committee)
    }

    protected fun persistContributionPeriod(): ContributionPeriod {
        val period = contributionPeriodFactory.createBasic()
        return persist(period)
    }

    protected fun persistSurvey(): Survey {
        val survey = surveyFactory.createBasic()
        return persist(survey)
    }

    protected fun fileWithUploader(file: File): File {
        val uploader = persistUser()
        entityManager.flush()
        file.uploader = uploader
        return file
    }

    protected fun persistEvent(): Event {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee
        event.signUp = false
        return persist(event)
    }

    protected fun persistQuestionWithSurvey(survey: Survey): Question {
        val question = questionFactory.createWithCustomizations {
            it.type = QuestionType.OPEN
            it.choiceLabels = null
            it.survey = survey
        }
        return persist(question)
    }

    protected fun persistTextAnswer(question: Question): Answer {
        val answer = answerFactory.createWithCustomizations {
            it.question = question
            it.optionSelections = null
            it.textResponse = "Test answer"
        }
        return persist(answer)
    }
}
