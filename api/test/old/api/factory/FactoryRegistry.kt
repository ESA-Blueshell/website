package net.blueshell.api.factory

import net.blueshell.api.factory.model.*
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
import org.springframework.stereotype.Component

/**
 * Registry exposing model factories; used by UnifiedFactory to discover creators.
 */
@Component
class FactoryRegistry(
    private val userFactory: UserFactory,
    private val addressFactory: AddressFactory,
    private val fileFactory: FileFactory,
    private val committeeFactory: CommitteeFactory,
    private val committeeMemberFactory: CommitteeMemberFactory,
    private val eventFactory: EventFactory,
    private val guestFactory: GuestFactory,
    private val surveyFactory: SurveyFactory,
    private val questionFactory: QuestionFactory,
    private val boardFactory: BoardFactory,
    private val blogFactory: BlogFactory,
    private val membershipFactory: MembershipFactory,
    private val answerFactory: AnswerFactory,
    private val eventBannerFactory: EventBannerFactory,
    private val eventPictureFactory: EventPictureFactory,
    private val eventSignUpFactory: EventSignUpFactory,
    private val eventSignUpAnswerFactory: EventSignUpAnswerFactory,
    private val eventFeedbackFactory: EventFeedbackFactory,
    private val telemetryFactory: TelemetryFactory,
    private val redirectFactory: RedirectFactory,
    private val boardMemberFactory: BoardMemberFactory,
    private val boardDocumentFactory: BoardDocumentFactory,
    private val contributionPeriodFactory: ContributionPeriodFactory,
    private val contributionFactory: ContributionFactory,
    private val contributionReminderFactory: ContributionReminderFactory,
    private val sponsorFactory: SponsorFactory,
    private val recoveryTokenFactory: RecoveryTokenFactory
) {

    fun user(): UserFactory = userFactory
    fun address(): AddressFactory = addressFactory
    fun file(): FileFactory = fileFactory
    fun committee(): CommitteeFactory = committeeFactory
    fun committeeMember(): CommitteeMemberFactory = committeeMemberFactory
    fun event(): EventFactory = eventFactory
    fun guest(): GuestFactory = guestFactory
    fun survey(): SurveyFactory = surveyFactory
    fun question(): QuestionFactory = questionFactory
    fun board(): BoardFactory = boardFactory
    fun blog(): BlogFactory = blogFactory
    fun membership(): MembershipFactory = membershipFactory
    fun answer(): AnswerFactory = answerFactory
    fun eventBanner(): EventBannerFactory = eventBannerFactory
    fun eventPicture(): EventPictureFactory = eventPictureFactory
    fun eventSignUp(): EventSignUpFactory = eventSignUpFactory
    fun eventSignUpAnswer(): EventSignUpAnswerFactory = eventSignUpAnswerFactory
    fun eventFeedback(): EventFeedbackFactory = eventFeedbackFactory
    fun telemetry(): TelemetryFactory = telemetryFactory
    fun redirect(): RedirectFactory = redirectFactory
    fun boardMember(): BoardMemberFactory = boardMemberFactory
    fun boardDocument(): BoardDocumentFactory = boardDocumentFactory
    fun contributionPeriod(): ContributionPeriodFactory = contributionPeriodFactory
    fun contribution(): ContributionFactory = contributionFactory
    fun contributionReminder(): ContributionReminderFactory = contributionReminderFactory
    fun sponsor(): SponsorFactory = sponsorFactory
    fun recoveryToken(): RecoveryTokenFactory = recoveryTokenFactory
}
