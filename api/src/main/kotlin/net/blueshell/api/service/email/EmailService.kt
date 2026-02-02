package net.blueshell.api.service.email

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.BaseEmail
import net.blueshell.api.common.enums.ResetType
import net.blueshell.api.email.ContributionReminderEmail
import net.blueshell.api.email.EventSignupEmail
import net.blueshell.api.email.recovery.MemberActivationEmail
import net.blueshell.api.email.recovery.PasswordResetEmail
import net.blueshell.api.email.recovery.UserActivationEmail
import net.blueshell.api.service.UserService
import net.blueshell.api.service.contribution.ContributionReminderService
import net.blueshell.api.service.event.EventSignUpService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
class EmailService(
    private val templateService: EmailTemplateService,
    private val mailDelivery: EmailDeliveryService,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val eventSignUps: EventSignUpService,
    @param:Value("\${frontend.url}") private val frontendUrl: String?,
    @param:Value("\${app.url}") private val appUrl: String?
) {
    fun sendContributionReminderEmail(reminderId: Long?) {
        val reminder = reminders.findById(reminderId)
        if (reminder == null || reminder.getUser() == null) return

        val email: BaseEmail = ContributionReminderEmail(
            reminder.getUser(),
            frontendUrl,
            appUrl,
            reminder.getContributionPeriod()
        )
        deliver(email)
    }

    fun sendEventSignupEmail(eventSignUpId: Long?) {
        val eventSignUp = eventSignUps.findById(eventSignUpId)
        if (eventSignUp == null) return

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
            content.recipient.getEmail(),
            content.subject,
            htmlContent,
            content.senderName,
            content.senderAddress
        )
    }

    fun sendUserResetEmail(userId: Long?, token: String?, resetType: ResetType?) {
        val user = users.findById(userId)
        if (user == null || resetType == null || token == null) {
            EmailService.log.info("Activation skipped: user={} or resetType missing", userId)
            return
        } else {
            EmailService.log.info("Sending {} email for user={}", resetType, userId)
        }

        val email: BaseEmail? = when (resetType) {
            ResetType.MEMBER_ACTIVATION -> MemberActivationEmail(user, token, frontendUrl, appUrl)
            ResetType.USER_ACTIVATION -> UserActivationEmail(user, token, frontendUrl, appUrl)
            ResetType.PASSWORD_RESET -> PasswordResetEmail(user, token, frontendUrl, appUrl)
            else -> null
        }
        if (email == null) return

        deliver(email)
    }
}
