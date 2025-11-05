package net.blueshell.api.factory;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.factory.model.*;
import org.springframework.stereotype.Component;

/**
 * Registry exposing model factories; used by UnifiedFactory to discover creators.
 */
@Component
@RequiredArgsConstructor
public class FactoryRegistry {

    private final UserFactory userFactory;
    private final AddressFactory addressFactory;
    private final FileFactory fileFactory;
    private final CommitteeFactory committeeFactory;
    private final CommitteeMemberFactory committeeMemberFactory;
    private final EventFactory eventFactory;
    private final GuestFactory guestFactory;
    private final SurveyFactory surveyFactory;
    private final QuestionFactory questionFactory;
    private final BoardFactory boardFactory;
    private final BlogFactory blogFactory;
    private final MembershipFactory membershipFactory;
    private final AnswerFactory answerFactory;
    private final EventBannerFactory eventBannerFactory;
    private final EventPictureFactory eventPictureFactory;
    private final EventSignUpFactory eventSignUpFactory;
    private final EventSignUpAnswerFactory eventSignUpAnswerFactory;
    private final EventFeedbackFactory eventFeedbackFactory;
    private final TelemetryFactory telemetryFactory;
    private final RedirectFactory redirectFactory;
    private final BoardMemberFactory boardMemberFactory;
    private final BoardDocumentFactory boardDocumentFactory;
    private final ContributionPeriodFactory contributionPeriodFactory;
    private final ContributionFactory contributionFactory;
    private final ContributionReminderFactory contributionReminderFactory;
    private final SponsorFactory sponsorFactory;
    private final RecoveryTokenFactory recoveryTokenFactory;

    public UserFactory user() { return userFactory; }
    public AddressFactory address() { return addressFactory; }
    public FileFactory file() { return fileFactory; }
    public CommitteeFactory committee() { return committeeFactory; }
    public CommitteeMemberFactory committeeMember() { return committeeMemberFactory; }
    public EventFactory event() { return eventFactory; }
    public GuestFactory guest() { return guestFactory; }
    public SurveyFactory survey() { return surveyFactory; }
    public QuestionFactory question() { return questionFactory; }
    public BoardFactory board() { return boardFactory; }
    public BlogFactory blog() { return blogFactory; }
    public MembershipFactory membership() { return membershipFactory; }
    public AnswerFactory answer() { return answerFactory; }
    public EventBannerFactory eventBanner() { return eventBannerFactory; }
    public EventPictureFactory eventPicture() { return eventPictureFactory; }
    public EventSignUpFactory eventSignUp() { return eventSignUpFactory; }
    public EventSignUpAnswerFactory eventSignUpAnswer() { return eventSignUpAnswerFactory; }
    public EventFeedbackFactory eventFeedback() { return eventFeedbackFactory; }
    public TelemetryFactory telemetry() { return telemetryFactory; }
    public RedirectFactory redirect() { return redirectFactory; }
    public BoardMemberFactory boardMember() { return boardMemberFactory; }
    public BoardDocumentFactory boardDocument() { return boardDocumentFactory; }
    public ContributionPeriodFactory contributionPeriod() { return contributionPeriodFactory; }
    public ContributionFactory contribution() { return contributionFactory; }
    public ContributionReminderFactory contributionReminder() { return contributionReminderFactory; }
    public SponsorFactory sponsor() { return sponsorFactory; }
    public RecoveryTokenFactory recoveryToken() { return recoveryTokenFactory; }
}
