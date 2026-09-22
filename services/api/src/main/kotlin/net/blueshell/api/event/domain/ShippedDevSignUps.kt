package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.event.persistence.GuestAccessTokenCodec
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.seed.SeedOrder
import net.blueshell.api.survey.api.QuestionData
import net.blueshell.api.survey.api.SurveyData
import net.blueshell.api.survey.api.SurveyFactory
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Puts a roster a board member can act on into a development database on start.
 *
 * Development only, and only where the first of its events is absent, so a second start neither
 * doubles the roster nor overwrites what somebody has been editing.
 */
@Component
@Profile("dev")
class ShippedDevSignUps(
    private val events: EventRepository,
    private val signUps: EventSignUpRepository,
    private val committees: CommitteeService,
    private val surveys: SurveyFactory,
    private val users: UserService,
    private val transactions: TransactionTemplate,
) {
    /** What a run wrote, which is nothing at all on every start after the first. */
    data class Applied(
        val events: Int,
        val signUps: Int,
    )

    @Order(SeedOrder.SIGN_UPS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val applied =
            try {
                apply()
            } catch (e: Exception) {
                log.warn("[dev-signups] the sign-up roster could not be written: {}", e.message)
                return
            }
        if (applied.events > 0) {
            log.info("[dev-signups] {} events and {} sign-ups are now in the database", applied.events, applied.signUps)
        }
    }

    fun apply(): Applied {
        if (events.existsByTitle(ROSTERS.first().title)) return Applied(0, 0)

        var written = 0
        ROSTERS.forEach { roster ->
            written +=
                transactions.execute {
                    val event = events.save(event(roster))
                    roster.signUps.count { signUp -> write(signUp, event) }
                } ?: 0
        }
        return Applied(events = ROSTERS.size, signUps = written)
    }

    private fun event(roster: Roster): Event {
        val start = Instant.now().plus(roster.daysAway, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
        return Event(
            committee = committees.findAll().firstOrNull(),
            title = roster.title,
            description = roster.description,
            location = roster.location,
            startTime = start,
            endTime = start.plus(EVENT_HOURS, ChronoUnit.HOURS),
            approved = true,
            membersOnly = roster.membersOnly,
            signUp = true,
            signUpDeadline = start.minus(1, ChronoUnit.DAYS),
        ).also { event ->
            roster.form?.let { event.replaceSignUpForm(surveys.createFromData(it)) }
        }
    }

    /** An account the fixtures never seeded leaves its row out rather than failing the whole roster. */
    private fun write(
        seed: SignUpSeed,
        event: Event,
    ): Boolean {
        val userId = seed.username?.let { username -> runCatching { users.findByUsername(username) }.getOrNull()?.id }
        if (seed.username != null && userId == null) {
            log.warn("[dev-signups] there is no '{}' account to sign up", seed.username)
            return false
        }

        val signUp =
            EventSignUp(
                event = event,
                userId = userId,
                guest = seed.guest?.let(::guest),
            )
        answer(seed, event, signUp)
        signUps.save(signUp)
        return true
    }

    private fun guest(seed: GuestSeed): Guest =
        Guest.withRawToken(
            name = seed.name,
            discord = seed.discord,
            email = seed.email,
            phoneNumber = seed.phoneNumber,
            accessToken = GuestAccessTokenCodec.generate(),
        )

    /** A seed that names no answer leaves the row unanswered, which the roster draws as such. */
    private fun answer(
        seed: SignUpSeed,
        event: Event,
        signUp: EventSignUp,
    ) {
        val questions = event.signUpForm?.questions?.associateBy { it.idx } ?: return
        val answers = signUp.answers as MutableSet<Answer>
        seed.answers.forEach { answerSeed ->
            val question = questions[answerSeed.idx] ?: return@forEach
            answers.add(
                Answer(
                    question = question,
                    optionSelections = answerSeed.selections?.toMutableList(),
                    textResponse = answerSeed.text,
                ),
            )
        }
    }

    data class GuestSeed(
        val name: String,
        val discord: String,
        val email: String,
        val phoneNumber: String,
    )

    data class AnswerSeed(
        val idx: Long,
        val text: String? = null,
        val selections: List<Boolean>? = null,
    )

    /** A sign-up belongs to an account or to a guest, never to both and never to neither. */
    data class SignUpSeed(
        val username: String? = null,
        val guest: GuestSeed? = null,
        val answers: List<AnswerSeed> = emptyList(),
    )

    data class Roster(
        val title: String,
        val description: String,
        val location: String,
        val daysAway: Long,
        val membersOnly: Boolean,
        val form: SurveyData?,
        val signUps: List<SignUpSeed>,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ShippedDevSignUps::class.java)
        private const val EVENT_HOURS = 4L

        private val OPEN_FORM =
            SurveyData(
                questions =
                    listOf(
                        QuestionData(
                            idx = 0,
                            type = QuestionType.OPEN,
                            label = "What would you like to play?",
                            choiceLabels = null,
                            required = true,
                        ),
                        QuestionData(
                            idx = 1,
                            type = QuestionType.RADIO,
                            label = "How are you getting there?",
                            choiceLabels = listOf("Bike", "Bus", "Car"),
                            required = true,
                        ),
                        QuestionData(
                            idx = 2,
                            type = QuestionType.CHECKBOX,
                            label = "Anything you do not eat?",
                            choiceLabels = listOf("Vegetarian", "Vegan", "Gluten-free"),
                            required = false,
                        ),
                    ),
            )

        /** The two events a development roster is spread over. The first doubles as the marker. */
        val ROSTERS: List<Roster> =
            listOf(
                Roster(
                    title = "Dev Open Game Night",
                    description =
                        "Seeded for development: a public event with a sign-up form, guests and accounts " +
                            "on its roster, so the board edit and the move onto an account are reachable.",
                    location = "Bastille",
                    daysAway = 10,
                    membersOnly = false,
                    form = OPEN_FORM,
                    signUps =
                        listOf(
                            SignUpSeed(
                                username = "member.paid",
                                answers =
                                    listOf(
                                        AnswerSeed(idx = 0, text = "Trackmania"),
                                        AnswerSeed(idx = 1, selections = listOf(true, false, false)),
                                        AnswerSeed(idx = 2, selections = listOf(true, false, false)),
                                    ),
                            ),
                            SignUpSeed(
                                username = "guest.unconfirmed",
                                answers =
                                    listOf(
                                        AnswerSeed(idx = 0, text = "Whatever is on"),
                                        AnswerSeed(idx = 1, selections = listOf(false, true, false)),
                                    ),
                            ),
                            SignUpSeed(
                                guest =
                                    GuestSeed(
                                        name = "Sam Sofa",
                                        discord = "samsofa#0001",
                                        email = "sam.sofa@example.test",
                                        phoneNumber = "+31610001001",
                                    ),
                                answers =
                                    listOf(
                                        AnswerSeed(idx = 0, text = "Valorant"),
                                        AnswerSeed(idx = 1, selections = listOf(false, false, true)),
                                        AnswerSeed(idx = 2, selections = listOf(false, true, false)),
                                    ),
                            ),
                            SignUpSeed(
                                guest =
                                    GuestSeed(
                                        name = "Robin Reed",
                                        discord = "robinreed#0001",
                                        email = "robin.reed@example.test",
                                        phoneNumber = "+31610001002",
                                    ),
                            ),
                            // Shares an account holder's name: moving this one onto member.paid is the 409.
                            SignUpSeed(
                                guest =
                                    GuestSeed(
                                        name = "Mila Paid",
                                        discord = "milapaid#0002",
                                        email = "member.paid@blueshell.test",
                                        phoneNumber = "+31610001003",
                                    ),
                                answers = listOf(AnswerSeed(idx = 0, text = "Signed up twice by mistake")),
                            ),
                        ),
                ),
                Roster(
                    title = "Dev Members Dinner",
                    description =
                        "Seeded for development: members-only and without a form, so an account sign-up " +
                            "has nothing to edit, and moving the guest onto a non-member is refused.",
                    location = "Vestingbar",
                    daysAway = 17,
                    membersOnly = true,
                    form = null,
                    signUps =
                        listOf(
                            SignUpSeed(username = "member.unpaid"),
                            SignUpSeed(username = "member.alumni"),
                            SignUpSeed(
                                guest =
                                    GuestSeed(
                                        name = "Noa Plus-One",
                                        discord = "noaplusone#0001",
                                        email = "noa.plusone@example.test",
                                        phoneNumber = "+31610001004",
                                    ),
                            ),
                        ),
                ),
            )
    }
}
