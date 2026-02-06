package net.blueshell.api.integration.mapper

import jakarta.persistence.EntityManager
import net.blueshell.api.common.enums.Role
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.model.File
import net.blueshell.api.model.User
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.api.model.event.Event
import net.blueshell.api.model.survey.Question
import net.blueshell.api.model.survey.Survey
import net.blueshell.api.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@Transactional
abstract class MapperTestSupport {
    @Autowired
    protected lateinit var entityManager: EntityManager

    @Autowired
    protected lateinit var userFactory: UserFactory

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var committeeFactory: CommitteeFactory

    @Autowired
    protected lateinit var contributionPeriodFactory: ContributionPeriodFactory

    @Autowired
    protected lateinit var surveyFactory: SurveyFactory

    @Autowired
    protected lateinit var questionFactory: QuestionFactory

    @Autowired
    protected lateinit var eventFactory: EventFactory

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    protected fun <T> persist(entity: T): T = entityManager.merge(entity)

    protected fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    protected fun <T> reload(type: Class<T>, id: Any): T {
        return requireNotNull(entityManager.find(type, id)) { "Expected ${type.simpleName} to be persisted" }
    }

    protected fun persistUser(): User = persist(userFactory.createBasic())

    protected fun persistCommittee(): Committee = persist(committeeFactory.createBasic())

    protected fun persistContributionPeriod(): ContributionPeriod = persist(contributionPeriodFactory.createBasic())

    protected fun persistSurvey(): Survey = persist(surveyFactory.createBasic())

    protected fun persistQuestionWithSurvey(survey: Survey): Question {
        val question = questionFactory.createForSurvey(survey)
        return persist(question)
    }

    protected fun persistEvent(): Event {
        val committee = persistCommittee()
        val event = eventFactory.createBasic().apply {
            this.committee = committee
            this.committeeId = committee.id
            signUp = false
        }
        return persist(event)
    }

    protected fun fileWithUploader(file: File): File {
        val uploader = persistUser()
        file.uploader = uploader
        return file
    }

    protected fun authenticateAs(role: Role): User {
        val user = userFactory.createWithRole(role)
        val saved = userRepository.save(user)
        val auth = UsernamePasswordAuthenticationToken(saved, null, saved.authorities)
        SecurityContextHolder.getContext().authentication = auth
        return saved
    }
}
