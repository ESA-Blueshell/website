package net.blueshell.api.factory.dto

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.platform.config.FactoryConfig
import net.blueshell.api.platform.config.PhoneNumberConfig
import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.user.persistence.UserRepository
import net.blueshell.api.survey.persistence.QuestionRepository
import net.blueshell.api.membership.application.MembershipService
import net.blueshell.api.event.application.EventService
import net.blueshell.api.testutil.ModelTestUtils
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.util.Optional

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@ContextConfiguration(classes = [DtoFactoryTestConfig::class])
abstract class DtoFactoryTestSupport {

    @Autowired
    protected lateinit var validator: Validator

    @Autowired
    protected lateinit var questionRepository: QuestionRepository

    @Autowired
    protected lateinit var eventService: EventService

    protected fun assertFactoryProducesValidDtos(factory: BaseDtoFactory<*>) {
        assertNoViolations(factory.createBasic() as Any)
        assertNoViolations(factory.createFull() as Any)
    }

    protected fun assertNoViolations(dto: Any) {
        assertNoViolations(dto, *emptyArray<Class<*>>())
    }

    protected fun assertNoViolations(dto: Any, vararg groups: Class<*>) {
        prepareValidation(dto)
        val violations = if (groups.isEmpty()) {
            validator.validate(dto)
        } else {
            validator.validate(dto, *groups)
        }
        assertTrue(violations.isEmpty(), violationMessage(dto, violations))
    }

    private fun prepareValidation(dto: Any) {
        clearMocks(questionRepository, eventService)
        when (dto) {
            is AnswerDTO -> {
                stubQuestionsForAnswers(listOf(dto))
            }

            is EventSignUpDTO -> {
                val questions = stubQuestionsForAnswers(dto.answers)
                stubEventForSignup(dto, questions)
            }

            else -> {
                every { questionRepository.findById(any()) } returns Optional.empty()
            }
        }
    }

    private fun stubQuestionsForAnswers(answers: List<AnswerDTO>): Map<Long, Question> {
        val questions = answers.mapNotNull { answer ->
            val questionId = answer.questionId ?: return@mapNotNull null
            val question = Question()
            ModelTestUtils.setId(question, questionId)
            question.type = inferQuestionType(answer)
            question.label = "Question $questionId"
            question.choiceLabels = when (question.type) {
                QuestionType.RADIO, QuestionType.CHECKBOX -> {
                    val size = answer.optionSelections?.size ?: 1
                    MutableList(size) { index -> "Option ${index + 1}" }
                }

                else -> null
            }
            question
        }.associateBy { it.id!! }

        every { questionRepository.findById(any()) } answers {
            val id = firstArg<Long>()
            Optional.ofNullable(questions[id])
        }
        return questions
    }

    private fun stubEventForSignup(dto: EventSignUpDTO, questions: Map<Long, Question>) {
        val eventId = dto.eventId ?: return
        val survey = Survey()
        ModelTestUtils.setId(survey, 1000L)
        val surveyQuestions = survey.questions
        questions.values.forEach { question ->
            question.survey = survey
            surveyQuestions.add(question)
        }

        val event = Event()
        ModelTestUtils.setId(event, eventId)
        event.signUpForm = survey

        every { eventService.findById(eventId) } returns event
    }

    private fun inferQuestionType(answer: AnswerDTO): QuestionType {
        if (!answer.textResponse.isNullOrBlank()) {
            return QuestionType.OPEN
        }
        val selections = answer.optionSelections
        if (selections != null) {
            val trueCount = selections.count { it }
            return if (trueCount == 1) QuestionType.RADIO else QuestionType.CHECKBOX
        }
        return QuestionType.DESCRIPTION
    }

    private fun violationMessage(dto: Any, violations: Set<ConstraintViolation<*>>): String {
        val details = violations.joinToString { violation ->
            "${violation.propertyPath}: ${violation.message} (invalid=${violation.invalidValue})"
        }
        return "Validation failed for ${dto::class.java.simpleName}: $details"
    }
}

@TestConfiguration
@ComponentScan(basePackages = ["net.blueshell.api.factory.dto"])
@Import(FactoryConfig::class, PhoneNumberConfig::class)
class DtoFactoryTestConfig {
    @Bean
    fun validator(applicationContext: ApplicationContext): LocalValidatorFactoryBean {
        return LocalValidatorFactoryBean().apply { setApplicationContext(applicationContext) }
    }

    @Bean
    fun questionRepository(): QuestionRepository = mockk(relaxed = true)

    @Bean
    fun userRepository(): UserRepository = mockk(relaxed = true)

    @Bean
    fun userService(): net.blueshell.api.user.application.UserService = mockk(relaxed = true)

    @Bean
    fun membershipService(): MembershipService = mockk(relaxed = true)

    @Bean
    fun eventService(): EventService = mockk(relaxed = true)

    @Bean
    fun entityManagerFactory(): EntityManagerFactory {
        val em: EntityManager = mockk(relaxed = true)
        val emf: EntityManagerFactory = mockk(relaxed = true)
        every { emf.createEntityManager() } returns em
        return emf
    }
}
