package net.blueshell.api.factory.dto

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.config.FactoryConfig
import net.blueshell.api.config.PhoneNumberConfig
import net.blueshell.api.dto.MembershipDTO
import net.blueshell.api.dto.event.EventSignUpDTO
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.dto.user.AdvancedUserDTO
import net.blueshell.api.dto.user.SimpleUserDTO
import net.blueshell.api.validation.group.Administration
import net.blueshell.api.validation.group.Creation
import net.blueshell.api.validation.group.Update
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.factory.dto.committee.CommitteeMemberDTOFactory
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.model.event.Event
import net.blueshell.api.model.survey.Question
import net.blueshell.api.model.survey.Survey
import net.blueshell.api.repository.UserRepository
import net.blueshell.api.repository.survey.QuestionRepository
import net.blueshell.api.service.MembershipService
import net.blueshell.api.service.event.EventService
import net.blueshell.api.testutil.ModelTestUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.util.*

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@ContextConfiguration(classes = [DtoFactoryValidationTest.DtoFactoryTestConfig::class])
class DtoFactoryValidationTest {

    @Autowired
    private lateinit var validator: Validator

    @Autowired
    private lateinit var factories: List<BaseDtoFactory<*>>

    @Autowired
    private lateinit var questionRepository: QuestionRepository

    @Autowired
    private lateinit var eventService: EventService

    @Autowired
    private lateinit var advancedCommitteeDTOFactory: AdvancedCommitteeDTOFactory

    @Autowired
    private lateinit var committeeMemberDTOFactory: CommitteeMemberDTOFactory

    @Autowired
    private lateinit var answerDTOFactory: AnswerDTOFactory

    @Autowired
    private lateinit var surveyDTOFactory: SurveyDTOFactory

    @TestFactory
    fun dtoFactoriesProduceValidDtos(): List<DynamicTest> {
        return factories.sortedBy { it.targetType().simpleName }.flatMap { factory ->
            listOf(DynamicTest.dynamicTest("${factory.targetType().simpleName} createBasic") {
                assertNoViolations(factory.createBasic())
            }, DynamicTest.dynamicTest("${factory.targetType().simpleName} createFull") {
                assertNoViolations(factory.createFull())
            })
        }
    }

    @Test
    fun advancedCommitteeFactoryAssignsStandardBoardRoles() {
        val dto = advancedCommitteeDTOFactory.createWithMemberCount(3)
        val roles = dto.members.mapNotNull { it?.role }
        assertEquals(listOf("Chair", "Secretary", "Treasurer"), roles)
        assertNoViolations(dto)
    }

    @Test
    fun advancedCommitteeFactoryRejectsZeroMembers() {
        assertThrows(IllegalArgumentException::class.java) {
            advancedCommitteeDTOFactory.createWithMemberCount(0)
        }
    }

    @Test
    fun committeeMemberFactoryCreatesRoleHelpers() {
        val chair = committeeMemberDTOFactory.createChair()
        assertEquals("Chair", chair.role)
        assertNoViolations(chair)
    }

    @Test
    fun answerFactoryCreatesValidRadioSelections() {
        val answer = answerDTOFactory.createForRadioQuestion(4, 2)
        assertEquals(1, answer.optionSelections?.count { it == true })
        assertEquals(4, answer.optionSelections?.size)
        assertNoViolations(answer)
    }

    @Test
    fun answerFactoryCreatesValidCheckboxSelections() {
        val answer = answerDTOFactory.createForCheckboxQuestion(5, listOf(1, 3))
        assertEquals(5, answer.optionSelections?.size)
        assertNoViolations(answer)
    }

    @Test
    fun surveyFactoryAssignsSequentialIndexes() {
        val survey = surveyDTOFactory.createWithQuestionTypes(
            QuestionType.OPEN, QuestionType.RADIO, QuestionType.CHECKBOX
        )
        val indexes = survey.questions?.mapNotNull { it?.idx } ?: emptyList()
        assertEquals(listOf(1L, 2L, 3L), indexes)
        assertNoViolations(survey)
    }

    @Test
    fun userDtosValidateControllerGroups() {
        val advancedUser = factoryFor(AdvancedUserDTO::class.java).createBasic()
        assertNoViolations(advancedUser, Creation::class.java)
        assertNoViolations(advancedUser, Administration::class.java)
        assertNoViolations(advancedUser, Update::class.java)

        val simpleUser = factoryFor(SimpleUserDTO::class.java).createBasic()
        assertNoViolations(simpleUser, Creation::class.java)
        assertNoViolations(simpleUser, Update::class.java)
    }

    @Test
    fun membershipDtoValidatesAdministrationGroup() {
        val membership = factoryFor(MembershipDTO::class.java).createBasic()
        assertNoViolations(membership, Administration::class.java)
    }

    private fun assertNoViolations(dto: Any) {
        assertNoViolations(dto, *emptyArray<Class<*>>())
    }

    private fun assertNoViolations(dto: Any, vararg groups: Class<*>) {
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
        val surveyQuestions = survey.questions as MutableSet<Question>
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
            val trueCount = selections.count { it == true }
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> factoryFor(type: Class<T>): BaseDtoFactory<T> {
        return factories.firstOrNull { it.targetType() == type } as? BaseDtoFactory<T>
            ?: throw IllegalArgumentException("No DTO factory registered for ${type.name}")
    }

    @Configuration
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
        fun userService(): net.blueshell.api.service.UserService = mockk(relaxed = true)

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
}
