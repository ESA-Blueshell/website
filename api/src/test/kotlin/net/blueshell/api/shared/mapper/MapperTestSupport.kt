package net.blueshell.api.shared.mapper

import jakarta.persistence.EntityManager
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.contribution.ContributionPeriodFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.file.persistence.File
import net.blueshell.api.platform.config.TruncateTestDatabaseListener
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.repository.UserRepository
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
            signUp = false
        }
        return persist(event)
    }

    protected fun fileWithUploader(file: File): File {
        val uploader = persistUser()
        entityManager.flush()
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
