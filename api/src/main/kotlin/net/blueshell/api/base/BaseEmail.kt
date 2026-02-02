package net.blueshell.api.base

import lombok.Getter
import net.blueshell.api.model.User

abstract class BaseEmail(
    @field:Getter protected val recipient: User?,
    protected val frontendUrl: String?,
    protected val appUrl: String?
) {
    /**
     * Get the email subject line
     */
    abstract val subject: String?

    /**
     * Generate the markdown content for the email
     */
    abstract val markdownContent: String?

    open val senderName: String?
        /**
         * Get the sender name for this email type
         */
        get() = "Blueshell"

    val senderAddress: String
        /**
         * Get the sender email address
         */
        get() = "sitecie@blueshell.utwente.nl"

    open val replyTo: String?
        get() = "sitecie@blueshell.utwente.nl"

    /**
     * Template method for building the complete email content
     */
    fun buildEmailContent(): EmailContent {
        return EmailContent(
            recipient,
            this.subject,
            this.markdownContent,
            this.senderName,
            this.senderAddress,
            this.replyTo
        )
    }
}