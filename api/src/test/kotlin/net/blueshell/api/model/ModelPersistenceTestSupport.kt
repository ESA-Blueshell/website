package net.blueshell.api.model

import jakarta.persistence.EntityManager
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.factory.model.BlogFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.factory.model.MembershipFactory
import net.blueshell.api.factory.model.RecoveryTokenFactory
import net.blueshell.api.factory.model.RedirectFactory
import net.blueshell.api.factory.model.SponsorFactory
import net.blueshell.api.factory.model.TelemetryFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.board.BoardDocumentFactory
import net.blueshell.api.factory.model.board.BoardFactory
import net.blueshell.api.factory.model.board.BoardMemberFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.committee.CommitteeMemberFactory
import net.blueshell.api.factory.model.contribution.ContributionFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.factory.model.contribution.ContributionReminderFactory
import net.blueshell.api.factory.model.event.EventBannerFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.event.EventFeedbackFactory
import net.blueshell.api.factory.model.event.EventPictureFactory
import net.blueshell.api.factory.model.event.EventSignUpAnswerFactory
import net.blueshell.api.factory.model.event.EventSignUpFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.event.Event
import net.blueshell.api.model.survey.Answer
import net.blueshell.api.model.survey.Question
import net.blueshell.api.model.survey.Survey
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@Transactional
abstract class ModelPersistenceTestSupport {

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

    protected fun <T, ID> persistAndReload(entity: T, type: Class<T>, idExtractor: (T) -> ID?): T {
        val saved = persist(entity)
        entityManager.flush()
        entityManager.clear()
        val id = idExtractor(saved)
        assertNotNull(id, "Expected id for ${type.simpleName}")
        @Suppress("UNCHECKED_CAST")
        return entityManager.find(type, id as ID)
    }

    protected fun unique(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    protected fun timestamp(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    protected fun fileWithUploader(file: File): File {
        val uploader = persist(userFactory.createBasic())
        file.uploader = uploader
        return file
    }

    protected fun persistCommittee(): Committee = persist(committeeFactory.createBasic())

    protected fun persistSurvey(): Survey = persist(surveyFactory.createBasic())

    protected fun persistEvent(): Event {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee
        event.committeeId = committee.id
        event.signUp = false
        return persist(event)
    }

    protected fun persistQuestionWithSurvey(survey: Survey): Question {
        val question = questionFactory.createWithCustomizations {
            it.idx = 1
            it.type = QuestionType.OPEN
            it.label = unique("label")
            it.choiceLabels = null
            it.survey = survey
        }
        return persist(question)
    }

    protected fun persistAnswer(question: Question): Answer {
        val answer = answerFactory.createWithCustomizations {
            it.question = question
            it.questionId = question.id ?: 0
            it.optionSelections = mutableListOf(true, false)
            it.textResponse = "Answer"
        }
        return persist(answer)
    }
}
