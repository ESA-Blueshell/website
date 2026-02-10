package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.auth.application.email.MemberActivationEmail
import net.blueshell.api.auth.application.email.PasswordResetEmail
import net.blueshell.api.auth.application.email.UserActivationEmail
import net.blueshell.api.contribution.application.ContributionReminderService
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.platform.integration.email.model.ContributionReminderEmail
import net.blueshell.api.platform.integration.email.model.EventSignupEmail
import net.blueshell.api.platform.integration.email.model.base.BaseEmail
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.user.application.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val templateService: EmailTemplateService,
    private val mailDelivery: EmailDeliveryService,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val eventSignUps: EventSignUpService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
    @param:Value($$"${app.url}") private val appUrl: String
) {
    fun sendContributionReminderEmail(userId: Long, contributionPeriodId: Long) {
        val reminder = reminders.findById(ContributionReminder.Id(userId, contributionPeriodId))
        val email: BaseEmail = ContributionReminderEmail(
            reminder.user,
            frontendUrl,
            appUrl,
            reminder.contributionPeriod
        )
        deliver(email)
    }

    fun sendEventSignupEmail(eventSignUpId: Long) {
        val eventSignUp = eventSignUps.findById(eventSignUpId)
        val email: BaseEmail = EventSignupEmail(eventSignUp, frontendUrl, appUrl)
        deliver(email)
    }

    /**
     * Render via template service and send via delivery service
     */
    private fun deliver(email: BaseEmail) {
        val content = email.buildEmailContent()

        val htmlContent = templateService.createEmail(
            content.recipient,
            content.subject,
            content.markdownContent
        )

        mailDelivery.sendHtmlEmail(
            content.recipient.email,
            content.subject,
            htmlContent,
            content.senderName,
            content.senderAddress
        )
    }

    fun sendUserResetEmail(userId: Long, token: String, resetType: ResetType) {
        val user = users.findById(userId)
        log.info("Sending {} email for user={}", resetType, userId)

        val email: BaseEmail = when (resetType) {
            ResetType.MEMBER_ACTIVATION -> MemberActivationEmail(user, token, frontendUrl, appUrl)
            ResetType.USER_ACTIVATION -> UserActivationEmail(user, token, frontendUrl, appUrl)
            ResetType.PASSWORD_RESET -> PasswordResetEmail(user, token, frontendUrl, appUrl)
        }

        deliver(email)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailService::class.java)
    }
}
