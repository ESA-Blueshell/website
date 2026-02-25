package net.blueshell.api.system.frontend.events

import com.microsoft.playwright.Page
import net.blueshell.api.domain.user.application.erasure.UserErasureService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.EventSignUpAnswer
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.persistence.repository.AnswerRepository
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.function.Predicate

@Tag("system")
class EventSignUpsPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var answerRepository: AnswerRepository

    @Autowired
    private lateinit var persistence: FactoryPersistenceSupport

    @Autowired
    private lateinit var erasure: UserErasureService

    @Test
    fun `committee member sees sign-up respondents answers and totals`() {
        val seeded = seedEventSignUpsData()

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.viewer.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val signupsResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/events/${seeded.eventId}/signups")
                }
            ) {
                page.navigate("$frontendUrl/events/signups/${seeded.eventId}")
            }
            assertThat(signupsResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected respondent rows on sign-ups page for event=${seeded.eventId}" }
            ) {
                page.locator(".attendees-table tbody tr").count() >= 2
            }

            assertThat(
                page.getByText(seeded.guestName, Page.GetByTextOptions().setExact(true)).count()
            ).isGreaterThan(0)

            assertThat(
                page.getByText(seeded.memberOpenAnswer, Page.GetByTextOptions().setExact(true)).count()
            ).isGreaterThan(0)
            assertThat(
                page.getByText(seeded.guestOpenAnswer, Page.GetByTextOptions().setExact(true)).count()
            ).isGreaterThan(0)

            assertThat(questionTotals(page, seeded.radioQuestionLabel)).containsExactly("1", "1")
            assertThat(questionTotals(page, seeded.checkboxQuestionLabel)).containsExactly("1", "1", "1")
        }
    }

    @Test
    fun `non committee user gets forbidden response on sign-up list`() {
        val seeded = seedEventSignUpsData()

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.outsider.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val signupsResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/events/${seeded.eventId}/signups")
                }
            ) {
                page.navigate("$frontendUrl/events/signups/${seeded.eventId}")
            }
            assertThat(signupsResponse.status()).isIn(401, 403)

            assertThat(
                page.getByText(seeded.guestName, Page.GetByTextOptions().setExact(true)).count()
            ).isEqualTo(0)
        }
    }

    @Test
    fun `deleted signup user remains visible on sign-up page as anonymized identity`() {
        val seeded = seedEventSignUpsData()
        val deletedUserId = checkNotNull(seeded.memberRespondent.id) { "Expected member respondent id" }
        erasure.deleteUser(deletedUserId)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.viewer.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val signupsResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/events/${seeded.eventId}/signups")
                }
            ) {
                page.navigate("$frontendUrl/events/signups/${seeded.eventId}")
            }
            assertThat(signupsResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected respondent rows on sign-ups page for event=${seeded.eventId}" }
            ) {
                page.locator(".attendees-table tbody tr").count() >= 2
            }

            waitFor(
                onTimeoutMessage = {
                    "Expected deleted signup user to remain visible as anonymized identity 'Deleted User'"
                }
            ) {
                page.getByText("Deleted User", Page.GetByTextOptions().setExact(true)).count() > 0
            }

            assertThat(
                page.getByText(seeded.memberOpenAnswer, Page.GetByTextOptions().setExact(true)).count()
            ).isGreaterThan(0)
        }
    }

    private fun seedEventSignUpsData(): SeededSignUpsData {
        val marker = System.currentTimeMillis()

        val viewer = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val outsider = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val memberRespondent = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val committee = committeeFactory.create(name = "Signups Committee $marker")
        committeeFactory.createMember(committee, viewer)

        val event = eventFactory.create(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Event SignUps $marker"
        )

        val signUpForm = Survey()
        val openQuestionLabel = "What do you expect?"
        val radioQuestionLabel = "Do you need transport?"
        val checkboxQuestionLabel = "Preferred activities"
        signUpForm.replaceQuestions(
            listOf(
                Question(
                    idx = 0,
                    survey = signUpForm,
                    type = QuestionType.OPEN,
                    label = openQuestionLabel
                ),
                Question(
                    idx = 1,
                    survey = signUpForm,
                    type = QuestionType.RADIO,
                    label = radioQuestionLabel,
                    choiceLabels = mutableListOf("Yes", "No")
                ),
                Question(
                    idx = 2,
                    survey = signUpForm,
                    type = QuestionType.CHECKBOX,
                    label = checkboxQuestionLabel,
                    choiceLabels = mutableListOf("LAN", "Board Games", "Dinner")
                )
            )
        )
        event.replaceSignUpForm(signUpForm)
        val persistedEvent: Event = eventRepository.saveAndFlush(event)
        val eventId = checkNotNull(persistedEvent.id) { "Expected persisted event id" }

        val questionsByLabel = persistedEvent.signUpForm
            ?.questions
            ?.associateBy { it.label }
            .orEmpty()
        val openQuestion = checkNotNull(questionsByLabel[openQuestionLabel]) { "Expected open question to be persisted" }
        val radioQuestion = checkNotNull(questionsByLabel[radioQuestionLabel]) { "Expected radio question to be persisted" }
        val checkboxQuestion =
            checkNotNull(questionsByLabel[checkboxQuestionLabel]) { "Expected checkbox question to be persisted" }

        val memberSignUp = eventFactory.createSignUp(persistedEvent, user = memberRespondent)
        val guestName = "Guest $marker"
        val guest = eventFactory.createGuest(name = guestName, accessToken = "guest-token-$marker")
        val guestSignUp = eventFactory.createSignUp(persistedEvent, guest = guest)

        val memberOpenAnswer = "Member open answer $marker"
        val guestOpenAnswer = "Guest open answer $marker"

        val memberAnswers = listOf(
            answerRepository.saveAndFlush(Answer(question = openQuestion, textResponse = memberOpenAnswer)),
            answerRepository.saveAndFlush(
                Answer(
                    question = radioQuestion,
                    optionSelections = mutableListOf(true, false)
                )
            ),
            answerRepository.saveAndFlush(
                Answer(
                    question = checkboxQuestion,
                    optionSelections = mutableListOf(true, false, true)
                )
            )
        )
        val guestAnswers = listOf(
            answerRepository.saveAndFlush(Answer(question = openQuestion, textResponse = guestOpenAnswer)),
            answerRepository.saveAndFlush(
                Answer(
                    question = radioQuestion,
                    optionSelections = mutableListOf(false, true)
                )
            ),
            answerRepository.saveAndFlush(
                Answer(
                    question = checkboxQuestion,
                    optionSelections = mutableListOf(false, true, false)
                )
            )
        )

        memberAnswers.forEach { answer ->
            persistence.persist(
                EventSignUpAnswer(
                    eventSignUp = memberSignUp,
                    answer = answer
                )
            )
        }
        guestAnswers.forEach { answer ->
            persistence.persist(
                EventSignUpAnswer(
                    eventSignUp = guestSignUp,
                    answer = answer
                )
            )
        }

        return SeededSignUpsData(
            eventId = eventId,
            viewer = viewer,
            outsider = outsider,
            memberRespondent = memberRespondent,
            guestName = guestName,
            memberOpenAnswer = memberOpenAnswer,
            guestOpenAnswer = guestOpenAnswer,
            radioQuestionLabel = radioQuestionLabel,
            checkboxQuestionLabel = checkboxQuestionLabel
        )
    }

    private fun questionTotals(page: Page, questionLabel: String): List<String> {
        val questionCard = page.locator(".v-card:has(.v-card-title:has-text(\"$questionLabel\"))").first()
        val totalsCells = questionCard.locator(".radio-table tfoot tr td")

        waitFor(
            onTimeoutMessage = { "Expected totals row to be visible for question '$questionLabel'" }
        ) {
            totalsCells.count() > 1
        }

        return (1 until totalsCells.count())
            .map { idx -> totalsCells.nth(idx).innerText().trim() }
    }

    private data class SeededSignUpsData(
        val eventId: Long,
        val viewer: net.blueshell.api.domain.user.persistence.User,
        val outsider: net.blueshell.api.domain.user.persistence.User,
        val memberRespondent: net.blueshell.api.domain.user.persistence.User,
        val guestName: String,
        val memberOpenAnswer: String,
        val guestOpenAnswer: String,
        val radioQuestionLabel: String,
        val checkboxQuestionLabel: String
    )

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
