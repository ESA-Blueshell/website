package net.blueshell.api.system.frontend.events

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.function.Predicate

@Tag("system")
class EventSignUpsPageSystemTest : PlaywrightTestBase() {
    @Test
    fun `committee member sees sign-up respondents answers and totals`() {
        val seeded = seedEventSignUpsData()

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.viewer.username, seeded.viewer.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/${seeded.eventId}")

        pollFor("respondent rows on sign-ups page for event=${seeded.eventId}") {
            page.locator(".attendees-table tbody tr").count() >= 2
        }

        assertThat(
            page.getByText(seeded.guestName, Page.GetByTextOptions().setExact(true)).count(),
        ).isGreaterThan(0)

        assertThat(
            page.getByText(seeded.memberOpenAnswer, Page.GetByTextOptions().setExact(true)).count(),
        ).isGreaterThan(0)
        assertThat(
            page.getByText(seeded.guestOpenAnswer, Page.GetByTextOptions().setExact(true)).count(),
        ).isGreaterThan(0)

        assertThat(questionTotals(page, seeded.radioQuestionLabel)).containsExactly("1", "1")
        assertThat(questionTotals(page, seeded.checkboxQuestionLabel)).containsExactly("1", "1", "1")
    }

    @Test
    fun `non committee user gets forbidden response on sign-up list`() {
        val seeded = seedEventSignUpsData()

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.outsider.username, seeded.outsider.password)
        assertThat(loginStatus).isEqualTo(200)

        val signupsResponse =
            page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/events/${seeded.eventId}/signups")
                },
            ) {
                page.navigate("$frontendUrl/events/signups/${seeded.eventId}")
            }
        assertThat(signupsResponse.status()).isIn(401, 403)

        assertThat(
            page.getByText(seeded.guestName, Page.GetByTextOptions().setExact(true)).count(),
        ).isEqualTo(0)
    }

    @Test
    fun `sign-ups page renders placeholder when an answer is missing after a question is added`() {
        val viewer = TestHelper.registerActivateAndPromote("COMMITTEE")
        val respondent = TestHelper.registerActivateAndPromote("MEMBER")
        val respondentId = TestHelper.findUser(respondent.username)!!.id

        val committeeId = TestHelper.createCommittee(name = "Missing Answers Committee ${TestHelper.uniqueSuffix()}")
        TestHelper.addCommitteeMember(committeeId, viewer.username)

        val eventId =
            TestHelper.createEvent(
                committeeId = committeeId,
                title = "Missing Answers Event ${TestHelper.uniqueSuffix()}",
                startTime = Instant.now().plusSeconds(2 * 3600),
                endTime = Instant.now().plusSeconds(3 * 3600),
                approved = true,
                signUp = true,
            )

        val surveyId = TestHelper.attachSurveyToEvent(eventId)
        val originalLabel = "Original question"
        val laterLabel = "Question added later"

        val originalQuestionId =
            TestHelper.createQuestion(
                surveyId = surveyId,
                idx = 0,
                type = "OPEN",
                label = originalLabel,
                required = false,
            )

        val signUpId = TestHelper.createUserEventSignUp(eventId, respondentId)
        TestHelper.createEventSignUpAnswer(signUpId, originalQuestionId, textResponse = "answered up front")

        TestHelper.createQuestion(
            surveyId = surveyId,
            idx = 1,
            type = "CHECKBOX",
            label = laterLabel,
            choiceLabels = listOf("Yes", "No"),
            required = true,
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, viewer.username, viewer.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/$eventId")

        pollFor("answer row visible for question added later") {
            page.locator(".v-card:has(.v-card-title:has-text(\"$laterLabel\")) .radio-table tbody tr").count() >= 1
        }

        val laterQuestionCard = page.locator(".v-card:has(.v-card-title:has-text(\"$laterLabel\"))").first()
        val missingIcons = laterQuestionCard.locator(".radio-table tbody tr .mdi-minus")
        assertThat(missingIcons.count()).isEqualTo(2)
    }

    @Test
    fun `deleted signup user remains visible on sign-up page as anonymized identity`() {
        val seeded = seedEventSignUpsData()
        TestHelper.eraseUser(seeded.memberRespondent.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.viewer.username, seeded.viewer.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/${seeded.eventId}")

        pollFor("respondent rows on sign-ups page for event=${seeded.eventId}") {
            page.locator(".attendees-table tbody tr").count() >= 2
        }

        pollFor("deleted signup user appears as anonymized identity 'Deleted User'") {
            page.getByText("Deleted User", Page.GetByTextOptions().setExact(true)).count() > 0
        }

        assertThat(
            page.getByText(seeded.memberOpenAnswer, Page.GetByTextOptions().setExact(true)).count(),
        ).isGreaterThan(0)
    }

    @Test
    fun `board removes a sign-up and the row leaves the roster`() {
        val seeded = seedEventSignUpsData()
        val board = TestHelper.registerActivateAndPromote("BOARD")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/${seeded.eventId}")

        pollFor("respondent rows on sign-ups page for event=${seeded.eventId}") {
            page.locator(".attendees-table tbody tr").count() >= 2
        }

        page.getByTestId("signup-remove-btn-${seeded.guestSignUpId}").click()
        page.getByTestId("remove-signup-confirm-btn").click()

        pollFor("removed guest row leaves the roster") {
            page.getByText(seeded.guestName, Page.GetByTextOptions().setExact(true)).count() == 0
        }

        assertThat(page.locator(".attendees-table tbody tr").count()).isEqualTo(1)
    }

    @Test
    fun `committee member is offered no removal`() {
        val seeded = seedEventSignUpsData()

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, seeded.viewer.username, seeded.viewer.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/${seeded.eventId}")

        pollFor("respondent rows on sign-ups page for event=${seeded.eventId}") {
            page.locator(".attendees-table tbody tr").count() >= 2
        }

        assertThat(page.getByTestId("signup-remove-btn-${seeded.guestSignUpId}").count()).isEqualTo(0)
    }

    @Test
    fun `board edits a guest sign-up's own details`() {
        val seeded = seedEventSignUpsData()
        val board = TestHelper.registerActivateAndPromote("BOARD")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/${seeded.eventId}")

        pollFor("respondent rows on sign-ups page for event=${seeded.eventId}") {
            page.locator(".attendees-table tbody tr").count() >= 2
        }

        page.getByTestId("signup-edit-btn-${seeded.guestSignUpId}").click()
        pollFor("edit dialog open") { page.getByTestId("edit-signup-dialog").count() > 0 }

        val correctedName = "Corrected Guest ${TestHelper.uniqueSuffix()}"
        // VvField hands `name` to vee-validate, not to the input, so the field is reached by testid.
        val nameField = page.getByTestId("guest-form-name").locator("input").first()
        nameField.fill(correctedName)
        // Awaiting the request says whether the form refused the save, which a roster poll alone
        // reports as a timeout with no reason.
        page.waitForRequest(
            Predicate { request -> request.method() == "PUT" && request.url().contains("/events/signups/") },
        ) {
            page.getByTestId("event-signup-submit-btn").click()
        }

        pollFor("corrected guest name on the roster") {
            page.getByText(correctedName, Page.GetByTextOptions().setExact(true)).count() > 0
        }
    }

    @Test
    fun `board moves a guest sign-up onto an account`() {
        val seeded = seedEventSignUpsData()
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val claimant = TestHelper.registerActivateAndPromote("MEMBER")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events/signups/${seeded.eventId}")

        pollFor("respondent rows on sign-ups page for event=${seeded.eventId}") {
            page.locator(".attendees-table tbody tr").count() >= 2
        }

        page.getByTestId("signup-edit-btn-${seeded.guestSignUpId}").click()
        pollFor("edit dialog open") { page.getByTestId("edit-signup-dialog").count() > 0 }

        val picker = page.getByTestId("signup-reassign-picker").locator("input").first()
        picker.click()
        picker.fill(claimant.username)
        page.getByRole(AriaRole.OPTION).first().click()
        page.getByTestId("event-signup-submit-btn").click()

        pollFor("guest is gone from the roster") {
            page.getByText(seeded.guestName, Page.GetByTextOptions().setExact(true)).count() == 0
        }
    }

    private fun seedEventSignUpsData(): SeededSignUpsData {
        val marker = TestHelper.uniqueSuffix()

        val viewer = TestHelper.registerActivateAndPromote("COMMITTEE")
        val outsider = TestHelper.registerActivateAndPromote("MEMBER")
        val memberRespondent = TestHelper.registerActivateAndPromote("MEMBER")
        val viewerId = TestHelper.findUser(viewer.username)!!.id
        val memberRespondentId = TestHelper.findUser(memberRespondent.username)!!.id

        val committeeId = TestHelper.createCommittee(name = "Signups Committee $marker")
        TestHelper.addCommitteeMember(committeeId, viewer.username)

        val eventId =
            TestHelper.createEvent(
                committeeId = committeeId,
                title = "Event SignUps $marker",
                startTime = Instant.now().plusSeconds(2 * 3600),
                endTime = Instant.now().plusSeconds(3 * 3600),
                approved = true,
                signUp = true,
            )

        val surveyId = TestHelper.attachSurveyToEvent(eventId)
        val openQuestionLabel = "What do you expect?"
        val radioQuestionLabel = "Do you need transport?"
        val checkboxQuestionLabel = "Preferred activities"

        val openQuestionId =
            TestHelper.createQuestion(
                surveyId = surveyId,
                idx = 0,
                type = "OPEN",
                label = openQuestionLabel,
            )
        val radioQuestionId =
            TestHelper.createQuestion(
                surveyId = surveyId,
                idx = 1,
                type = "RADIO",
                label = radioQuestionLabel,
                choiceLabels = listOf("Yes", "No"),
            )
        val checkboxQuestionId =
            TestHelper.createQuestion(
                surveyId = surveyId,
                idx = 2,
                type = "CHECKBOX",
                label = checkboxQuestionLabel,
                choiceLabels = listOf("LAN", "Board Games", "Dinner"),
            )

        val memberSignUpId = TestHelper.createUserEventSignUp(eventId, memberRespondentId)
        val guestName = "Guest $marker"
        val guestId =
            TestHelper.createGuest(
                name = guestName,
                discord = "guest_$marker",
                email = "guest-$marker@example.com",
                accessToken = "guest-token-$marker",
            )
        val guestSignUpId = TestHelper.createGuestEventSignUp(eventId, guestId)

        val memberOpenAnswer = "Member open answer $marker"
        val guestOpenAnswer = "Guest open answer $marker"

        TestHelper.createEventSignUpAnswer(memberSignUpId, openQuestionId, textResponse = memberOpenAnswer)
        TestHelper.createEventSignUpAnswer(
            memberSignUpId,
            radioQuestionId,
            optionSelections = listOf(true, false),
        )
        TestHelper.createEventSignUpAnswer(
            memberSignUpId,
            checkboxQuestionId,
            optionSelections = listOf(true, false, true),
        )

        TestHelper.createEventSignUpAnswer(guestSignUpId, openQuestionId, textResponse = guestOpenAnswer)
        TestHelper.createEventSignUpAnswer(
            guestSignUpId,
            radioQuestionId,
            optionSelections = listOf(false, true),
        )
        TestHelper.createEventSignUpAnswer(
            guestSignUpId,
            checkboxQuestionId,
            optionSelections = listOf(false, true, false),
        )

        return SeededSignUpsData(
            eventId = eventId,
            guestSignUpId = guestSignUpId,
            viewer = viewer,
            viewerId = viewerId,
            outsider = outsider,
            memberRespondent = memberRespondent,
            memberRespondentId = memberRespondentId,
            guestName = guestName,
            memberOpenAnswer = memberOpenAnswer,
            guestOpenAnswer = guestOpenAnswer,
            radioQuestionLabel = radioQuestionLabel,
            checkboxQuestionLabel = checkboxQuestionLabel,
        )
    }

    private fun questionTotals(
        page: Page,
        questionLabel: String,
    ): List<String> {
        val questionCard = page.locator(".v-card:has(.v-card-title:has-text(\"$questionLabel\"))").first()
        val totalsCells = questionCard.locator(".radio-table tfoot tr td")

        pollFor("totals row visible for question '$questionLabel'") {
            totalsCells.count() > 1
        }

        return (1 until totalsCells.count())
            .map { idx -> totalsCells.nth(idx).innerText().trim() }
    }

    private data class SeededSignUpsData(
        val eventId: Long,
        val guestSignUpId: Long,
        val viewer: TestHelper.RegisteredUser,
        val viewerId: Long,
        val outsider: TestHelper.RegisteredUser,
        val memberRespondent: TestHelper.RegisteredUser,
        val memberRespondentId: Long,
        val guestName: String,
        val memberOpenAnswer: String,
        val guestOpenAnswer: String,
        val radioQuestionLabel: String,
        val checkboxQuestionLabel: String,
    )
}
