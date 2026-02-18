package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.domain.auth.application.email.createMemberActivationEmail
import net.blueshell.api.domain.auth.application.email.createPasswordResetEmail
import net.blueshell.api.domain.auth.application.email.createUserActivationEmail
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.email.createContributionReminderEmail
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.email.createEventSignupEmail
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.ResetType
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
        val emailContent = createContributionReminderEmail(
            reminder.user,
            reminder.contributionPeriod,
            frontendUrl
        )
        deliver(emailContent)
    }

    fun sendEventSignupEmail(eventSignUpId: Long) {
        val eventSignUp = eventSignUps.findById(eventSignUpId)
        val emailContent = createEventSignupEmail(eventSignUp, frontendUrl)
        deliver(emailContent)
    }

    /**
     * Render via template service and send via delivery service
     */
    private fun deliver(emailContent: EmailContent) {
        val htmlContent = templateService.createEmail(
            emailContent.recipientEmail,
            emailContent.recipientName,
            emailContent.subject,
            emailContent.markdownContent
        )

        mailDelivery.sendHtmlEmail(
            emailContent.recipientEmail,
            emailContent.subject,
            htmlContent,
            emailContent.senderName,
            emailContent.senderAddress
        )
    }

    fun sendUserResetEmail(userId: Long, token: String, resetType: ResetType) {
        val user = users.findById(userId)
        log.info("Sending {} email for user={}", resetType, userId)

        val emailContent = when (resetType) {
            ResetType.MEMBER_ACTIVATION -> createMemberActivationEmail(user, token, frontendUrl)
            ResetType.USER_ACTIVATION -> createUserActivationEmail(user, token, frontendUrl)
            ResetType.PASSWORD_RESET -> createPasswordResetEmail(user, token, frontendUrl)
        }

        deliver(emailContent)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailService::class.java)
    }
}
